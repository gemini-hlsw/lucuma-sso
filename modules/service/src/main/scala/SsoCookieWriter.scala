// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import org.http4s.ResponseCookie
import lucuma.sso.model.User
import lucuma.sso.client.SsoCookieReader
import pdi.jwt.JwtClaim
import java.time.Instant
import cats.effect.Sync
import cats.implicits._
import io.circe.Json
import io.circe.syntax._
import scala.concurrent.duration.FiniteDuration
import lucuma.sso.service.util.JwtEncoder
import org.http4s.HttpDate

trait SsoCookieWriter[F[_]] {

  // Claims
  def newClaim(user: User): F[JwtClaim]
  def renewedClaim(claim: JwtClaim): F[JwtClaim]

  // Cookies
  def newCookie(claim: JwtClaim, secure: Boolean): F[ResponseCookie]
  def newCookie(user: User, secure: Boolean): F[ResponseCookie]
  def removeCookie: F[ResponseCookie]

}

object SsoCookieWriter {

  private val JwtCookie = SsoCookieReader.JwtCookie
  private val lucumaUser   = SsoCookieReader.lucumaUser

  val HttpOnly = true // JS can't see the cookie
  val SameSite = org.http4s.SameSite.Lax // We don't care

  def apply[F[_]: Sync](
    jwtEncoder: JwtEncoder[F],
    jwtTimeout: FiniteDuration,
    domain:     Option[String]
  ): SsoCookieWriter[F] =
    new SsoCookieWriter[F] {

      val now: F[Instant] =
        Sync[F].delay(Instant.now)

      def newClaim(user: User): F[JwtClaim] =
        now.map { inst =>
          JwtClaim(
            content    = Json.obj(lucumaUser -> user.asJson).spaces2,
            issuer     = Some("lucuma-sso"),
            subject    = Some(user.id.value.toString()),
            audience   = Some(Set("lucuma")),
            expiration = Some(inst.plusSeconds(jwtTimeout.toSeconds).getEpochSecond),
            notBefore  = Some(inst.getEpochSecond),
            issuedAt   = Some(inst.getEpochSecond),
          )
        }

      def renewedClaim(claim: JwtClaim): F[JwtClaim] =
        now.map { inst =>
          JwtClaim(
            content    = claim.content,
            issuer     = Some("lucuma-sso"),
            subject    = claim.subject,
            audience   = Some(Set("lucuma")),
            expiration = Some(inst.plusSeconds(jwtTimeout.toSeconds).getEpochSecond),
            notBefore  = Some(inst.getEpochSecond),
            issuedAt   = Some(inst.getEpochSecond),
          )
        }

      def newCookie(clm: JwtClaim, secure: Boolean): F[ResponseCookie] =
        jwtEncoder.encode(clm).map { jwt =>
          ResponseCookie(
            name     = JwtCookie,
            content  = jwt,
            domain   = domain,
            sameSite = SameSite,
            secure   = secure,
            httpOnly = HttpOnly,
            path = Some("/"),
          )
        }

      def removeCookie: F[ResponseCookie] =
        ResponseCookie(
          name     = JwtCookie,
          content  = "",
          domain   = domain,
          sameSite = SameSite,
          secure   = false,
          httpOnly = false,
          path = Some("/"),
          expires = Some(HttpDate.Epoch),
          maxAge = Some(0L)
        ).pure[F]

      def newCookie(user: User, secure: Boolean): F[ResponseCookie] =
        newClaim(user).flatMap(newCookie(_, secure))

    }

}