// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.service

import org.http4s.ResponseCookie
import gpp.sso.model.User
import gpp.sso.client.SsoCookieReader
import pdi.jwt.JwtClaim
import java.time.Instant
import cats.effect.Sync
import cats.implicits._
import io.circe.Json
import io.circe.syntax._
import scala.concurrent.duration.FiniteDuration
import gpp.sso.service.util.JwtEncoder
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
  private val GppUser   = SsoCookieReader.GppUser

  val HttpOnly = true // JS can't see the cookie
  val SameSite = org.http4s.SameSite.None // We don't care

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
            content    = Json.obj(GppUser -> user.asJson).spaces2,
            issuer     = Some("gpp-sso"),
            subject    = Some(user.id.value.toString()),
            audience   = Some(Set("gpp")),
            expiration = Some(inst.plusSeconds(jwtTimeout.toSeconds).getEpochSecond),
            notBefore  = Some(inst.getEpochSecond),
            issuedAt   = Some(inst.getEpochSecond),
          )
        }

      def renewedClaim(claim: JwtClaim): F[JwtClaim] =
        now.map { inst =>
          JwtClaim(
            content    = claim.content,
            issuer     = Some("gpp-sso"),
            subject    = claim.subject,
            audience   = Some(Set("gpp")),
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
          httpOnly = HttpOnly,
          path = Some("/"),
          expires = Some(HttpDate.Epoch),
          maxAge = Some(0L)
        ).pure[F]

      def newCookie(user: User, secure: Boolean): F[ResponseCookie] =
        newClaim(user).flatMap(newCookie(_, secure))

    }

}