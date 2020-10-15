// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.data.OptionT
import cats.MonadError
import cats.syntax.all._
import java.util.UUID
import org.http4s.{ Request, ResponseCookie, HttpDate, SameSite }
import org.http4s.RequestCookie

trait CookieService[F[_]] {

  /** Construct a non-expiring session cookie containing the specified token. */
  def cookie(token: UUID): F[ResponseCookie]

  /** Construct an empty and expired session cookie. */
  def revocation: F[ResponseCookie]

  /** Find the session cookie in `req`, if any. */
  def findCookie(req: Request[F]): F[Option[RequestCookie]]

  /**
   * Find the session cookie in `req`, if any, and decode it as a UUID. If the cookie's content is
   * not a valid UUID an error will be raised in `F`.
   */
  def findToken(req: Request[F]): F[Option[UUID]]

}

object CookieService {

  val CookieName = "lucuma-refresh-token"

  def apply[F[_]: MonadError[*[_], Throwable]](
    domain: Option[String]
  ): CookieService[F] =
    new CookieService[F] {

      def cookie(token: UUID): F[ResponseCookie] =
        ResponseCookie(
          name     = CookieName,
          content  = token.toString(),
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
          path = Some("/"),
          expires = Some(HttpDate.Epoch),
          maxAge = Some(0L)
        ).pure[F]

      def findCookie(req: Request[F]): F[Option[RequestCookie]] =
        req.cookies.find(_.name == CookieName).pure[F]

      def findToken(req: Request[F]): F[Option[UUID]] =
        OptionT(findCookie(req)).semiflatMap { c =>
          Either.catchOnly[IllegalArgumentException](UUID.fromString(c.content)).liftTo[F]
        } .value

      }

}
