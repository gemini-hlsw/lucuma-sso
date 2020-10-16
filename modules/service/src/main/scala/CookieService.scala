// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.data.OptionT
import cats.MonadError
import cats.syntax.all._
import org.http4s.{ Request, Response, ResponseCookie, HttpDate, SameSite }
import org.http4s.RequestCookie
import java.util.UUID
import cats.Applicative

trait CookieReader[F[_]] {

  /** Find the session cookie in `req`, if any. */
  def findCookie(req: Request[F]): F[Option[RequestCookie]]

  /**
   * Find the session cookie in `req`, if any, and decode it as a SessionToken. If the cookie's content is
   * not a valid SessionToken an error will be raised in `F`.
   */
  def findSessionToken(req: Request[F]): F[Option[SessionToken]]

  /** Find the session cookie in `req`, if any. */
  def findCookie(res: Response[F]): F[Option[ResponseCookie]]

  /**
   * Find the session cookie in `res`, if any, and decode it as a SessionToken. If the cookie's content is
   * not a valid SessionToken an error will be raised in `F`.
   */
  def findSessionToken(res: Response[F]): F[Option[SessionToken]]

  def getSessionToken(res: Response[F]): F[SessionToken]

}

object CookieReader {

  private[service] val CookieName = "lucuma-refresh-token"

  def apply[F[_]: MonadError[*[_], Throwable]]: CookieReader[F] =
    new CookieReader[F] {

      def findCookie(req: Request[F]): F[Option[RequestCookie]] =
        req.cookies.find(_.name == CookieName).pure[F]

      def findSessionToken(req: Request[F]): F[Option[SessionToken]] =
        OptionT(findCookie(req)).semiflatMap { c =>
          Either.catchOnly[IllegalArgumentException](SessionToken(UUID.fromString(c.content))).liftTo[F]
        } .value

      def findCookie(res: Response[F]): F[Option[ResponseCookie]] =
        res.cookies.find(_.name == CookieName).pure[F]

      def findSessionToken(res: Response[F]): F[Option[SessionToken]] =
        OptionT(findCookie(res)).semiflatMap { c =>
          Either.catchOnly[IllegalArgumentException](SessionToken(UUID.fromString(c.content))).liftTo[F]
        } .value

      def getSessionToken(res: Response[F]): F[SessionToken] =
        findSessionToken(res).flatMap(_.toRight(new RuntimeException(s"Missing or invalid session token.")).liftTo[F])

    }
}

trait CookieWriter[F[_]] {

  /** Construct a non-expiring session cookie containing the specified token. */
  def sessionCookie(token: SessionToken): F[ResponseCookie]

  /** Construct an empty and expired session cookie. */
  def revocation: F[ResponseCookie]


}

object CookieWriter {

  private[service] val CookieName = CookieReader.CookieName

  def apply[F[_]: Applicative](
    domain: Option[String]
  ): CookieWriter[F] =
    new CookieWriter[F] {

      def sessionCookie(token: SessionToken): F[ResponseCookie] =
        ResponseCookie(
          name     = CookieName,
          content  = token.value.toString(),
          domain   = domain,
          sameSite = SameSite.None,
          secure   = true,
          httpOnly = true,
          path     = Some("/"),
        ).pure[F]

      def revocation: F[ResponseCookie] = ???
        ResponseCookie(
          name     = CookieName,
          content  = "",
          domain   = domain,
          sameSite = SameSite.None,
          secure   = false,
          httpOnly = false,
          path     = Some("/"),
          expires  = Some(HttpDate.Epoch),
          maxAge   = Some(0L)
        ).pure[F]

    }

}

trait CookieService[F[_]]
  extends CookieReader[F]
     with CookieWriter[F]

object CookieService {
    def apply[F[_]: MonadError[*[_], Throwable]](
      domain: Option[String]
    ): CookieService[F] =
      new CookieService[F] {
        val reader = CookieReader[F]
        val writer = CookieWriter[F](domain)
        def findCookie(req: Request[F]): F[Option[RequestCookie]] = reader.findCookie(req)
        def findSessionToken(req: Request[F]): F[Option[SessionToken]] = reader.findSessionToken(req)
        def findCookie(res: Response[F]): F[Option[ResponseCookie]] = reader.findCookie(res)
        def findSessionToken(res: Response[F]): F[Option[SessionToken]] = reader.findSessionToken(res)
        def sessionCookie(token: SessionToken): F[ResponseCookie] = writer.sessionCookie(token)
        def getSessionToken(res: Response[F]): F[SessionToken] = reader.getSessionToken(res)
        def revocation: F[ResponseCookie] = writer.revocation
      }
}