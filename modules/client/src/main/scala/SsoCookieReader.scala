// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client

import cats.data.OptionT
import cats.implicits._
import cats.MonadError
import lucuma.core.model.User
import lucuma.sso.client.codec.user._
import lucuma.sso.client.util.JwtDecoder
import io.circe.parser.parse
import org.http4s.Request
import pdi.jwt.JwtClaim
import org.http4s.RequestCookie
import org.http4s.Response
import org.http4s.ResponseCookie
import pdi.jwt.exceptions.JwtException

trait SsoCookieReader[F[_]] { outer =>

  // N.B. there is no existing way to abstract over request and response cookies and I'm not going
  //      to bother introducing a typeclass (it would take two actually since you also need to
  //      abstract over request and response) so we're using overloads and there's a bit of
  //      repetition in the implementation.

  // request
  def findCookie(req: Request[F]): F[Option[RequestCookie]]
  def findClaim(req: Request[F]): F[Option[JwtClaim]]
  def findUser(req: Request[F]): F[Option[User]]

  // request, with more info
  def attemptFindClaim(req: Request[F]): F[Option[Either[JwtException, JwtClaim]]]
  def attemptFindUser(req: Request[F]): F[Option[Either[JwtException, User]]]

  // response
  def findCookie(res: Response[F]): F[Option[ResponseCookie]]
  def findClaim(res: Response[F]): F[Option[JwtClaim]]
  def findUser(res: Response[F]): F[Option[User]]

}

object SsoCookieReader {

  private[sso] val JwtCookie  = "lucuma-jwt"
  private[sso] val lucumaUser = "lucuma-user"

  def apply[F[_]: MonadError[?[_], Throwable]](jwtDecoder: JwtDecoder[F]): SsoCookieReader[F] =
    new SsoCookieReader[F] {

      def attemptFindClaim(req: Request[F]): F[Option[Either[JwtException, JwtClaim]]] =
        findCookie(req).flatMap {
          case None    => none.pure[F]
          case Some(c) => jwtDecoder.attemptDecode(c.content).map(_.some)
        }

      def attemptFindUser(req: Request[F]): F[Option[Either[JwtException, User]]] =
        attemptFindClaim(req).flatMap {
          case None           => none.pure[F]
          case Some(Left(e))  => e.asLeft.some.pure[F]
          case Some(Right(c)) => decodeUser(c).map(u => u.asRight.some)
        }

      def findCookie(req: Request[F]): F[Option[RequestCookie]]  =
        req.cookies.find(_.name == JwtCookie).pure[F]

      def findClaim(req: Request[F]): F[Option[JwtClaim]] =
        OptionT(findCookie(req))
          .flatMapF(c => jwtDecoder.decodeOption(c.content))
          .value

      def findUser(req: Request[F]): F[Option[User]] =
        OptionT(findClaim(req)).semiflatMap(decodeUser).value

      def findCookie(res: Response[F]): F[Option[ResponseCookie]] =
        res.cookies.find(_.name == JwtCookie).pure[F]

      def findClaim(res: Response[F]): F[Option[JwtClaim]] =
        OptionT(findCookie(res))
          .semiflatMap(c => jwtDecoder.decode(c.content))
          .value

      def findUser(res: Response[F]): F[Option[User]] =
        OptionT(findClaim(res)).semiflatMap(decodeUser).value

      def decodeUser(claim: JwtClaim): F[User] =
        for {
          json  <- parse(claim.content).liftTo[F]
          user  <- json.hcursor.downField(lucumaUser).as[User].liftTo[F]
        } yield user

    }

}

