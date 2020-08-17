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
import org.http4s.SameSite

trait SsoCookieWriter[F[_]] {

  // Claims
  def newClaim(user: User): F[JwtClaim]
  def renewedClaim(claim: JwtClaim): F[JwtClaim]

  // Cookies
  def newCookie(claim: JwtClaim): F[ResponseCookie]
  def newCookie(user: User): F[ResponseCookie]

}

object SsoCookieWriter {

  private val JwtCookie = SsoCookieReader.JwtCookie
  private val GppUser   = SsoCookieReader.GppUser
  private val GppDomain = "gpp.gemini.edu"

  def apply[F[_]: Sync](
    jwtEncoder: JwtEncoder[F],
    jwtTimeout: FiniteDuration,
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

      def newCookie(clm: JwtClaim): F[ResponseCookie] =
        jwtEncoder.encode(clm).map { jwt =>
          ResponseCookie(
            name     = JwtCookie,
            content  = jwt,
            domain   = Some(GppDomain),
            sameSite = SameSite.None,
            secure   = true,
            httpOnly = true,
          )
        }

      def newCookie(user: User): F[ResponseCookie] =
        newClaim(user).flatMap(newCookie)

    }

}