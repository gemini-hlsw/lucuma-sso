// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client

import cats.data._
import cats.implicits._
import lucuma.core.model.User
import lucuma.sso.client.codec.user._
import lucuma.sso.client.util.JwtDecoder
import io.circe.parser.parse
import org.http4s.Request
import pdi.jwt.JwtClaim
import org.http4s.Response
import pdi.jwt.exceptions.JwtException
import org.http4s.headers.Authorization
import org.http4s.Credentials
import org.http4s.util.CaseInsensitiveString
import org.http4s.EntityDecoder
import cats.effect.Sync
import org.http4s.InvalidMessageBodyFailure

trait SsoJwtReader[F[_]] { outer =>

  /**
   * Retrieve the JWT from the `Authorization: Bearer <jwt>` header, if present, raising an
   * error in `F` if otherwise.
   */
  def findClaim(req: Request[F]): F[Option[JwtClaim]]

  /** Retrieve the user from the JWT, if present, raising an error in `F` otherwise. */
  def findUser(req: Request[F]): F[Option[User]]

  /** Retrieve the JWT from the `Authorization: Bearer <jwt>` header, if present. */
  def attemptFindClaim(req: Request[F]): F[Option[Either[JwtException, JwtClaim]]]

  /** Retrieve the User from the JWT, if present. */
  def attemptFindUser(req: Request[F]): F[Option[Either[JwtException, User]]]

  /** Retrieve the JWT from the response body. */
  def findClaim(res: Response[F]): F[JwtClaim]

  /** Retrieve the user from the response body. */
  def findUser(res: Response[F]): F[User]

  def getUser(claim: JwtClaim): F[User]

}

object SsoJwtReader {

  private[sso] val JwtCookie  = "lucuma-jwt"
  private[sso] val lucumaUser = "lucuma-user"

  def apply[F[_]: Sync](jwtDecoder: JwtDecoder[F]): SsoJwtReader[F] =
    new SsoJwtReader[F] {

      implicit val entityDecoderJwt: EntityDecoder[F, JwtClaim] =
        EntityDecoder.text[F].flatMapR { token =>
          println(token)
          EitherT(jwtDecoder.attemptDecode(token))
            .leftMap {
              case e: Exception => InvalidMessageBodyFailure(s"Invalid or missing JWT.", Some(e))
              case e            => InvalidMessageBodyFailure(s"Invalid or missing JWT: $e")
            }
        }

      val Bearer = CaseInsensitiveString("Bearer")

      def attemptFindClaim(req: Request[F]): F[Option[Either[JwtException, JwtClaim]]] =
        findBearerAuthorization(req).flatMap {
          case None    => none.pure[F]
          case Some(c) => jwtDecoder.attemptDecode(c).map(_.some)
        }

      def attemptFindUser(req: Request[F]): F[Option[Either[JwtException, User]]] =
        attemptFindClaim(req).flatMap {
          case None           => none.pure[F]
          case Some(Left(e))  => e.asLeft.some.pure[F]
          case Some(Right(c)) => getUser(c).map(u => u.asRight.some)
        }

      def findBearerAuthorization(req: Request[F]): F[Option[String]]  =
        req.headers.collectFirst {
          case Authorization(Authorization(Credentials.Token(Bearer, token))) => token
        } .pure[F]

      def findClaim(req: Request[F]): F[Option[JwtClaim]] =
        OptionT(findBearerAuthorization(req))
          .flatMapF(token => jwtDecoder.decodeOption(token))
          .value

      def findUser(req: Request[F]): F[Option[User]] =
        OptionT(findClaim(req)).semiflatMap(getUser).value

      def findClaim(res: Response[F]): F[JwtClaim] =
        res.as[JwtClaim]

      def findUser(res: Response[F]): F[User] =
        findClaim(res).flatMap(getUser)

      def getUser(claim: JwtClaim): F[User] =
        for {
          json  <- parse(claim.content).liftTo[F]
          user  <- json.hcursor.downField(lucumaUser).as[User].liftTo[F]
        } yield user

    }

}

