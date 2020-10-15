// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import lucuma.core.model.User
import lucuma.sso.client.SsoJwtReader
import pdi.jwt.JwtClaim
import java.time.Instant
import cats.effect.Sync
import cats.implicits._
import io.circe.Json
import io.circe.syntax._
import scala.concurrent.duration.FiniteDuration
import lucuma.sso.service.util.JwtEncoder
import lucuma.sso.client.codec.user._

trait SsoJwtWriter[F[_]] {

  // Claims
  def newClaim(user: User): F[JwtClaim]
  def renewedClaim(claim: JwtClaim): F[JwtClaim]

  // Encoded JWTs
  def newJwt(user: User): F[String]

}

object SsoJwtWriter {

  private val lucumaUser   = SsoJwtReader.lucumaUser

  val HttpOnly = true // JS can't see the cookie
  val SameSite = org.http4s.SameSite.None // We don't care

  def apply[F[_]: Sync](
    jwtEncoder: JwtEncoder[F],
    jwtTimeout: FiniteDuration,
  ): SsoJwtWriter[F] =
    new SsoJwtWriter[F] {

      val now: F[Instant] =
        Sync[F].delay(Instant.now)

      def newClaim(content: String, subject: Option[String]): F[JwtClaim] =
        now.map { inst =>
          JwtClaim(
            content    = content,
            issuer     = Some("lucuma-sso"),
            subject    = subject,
            audience   = Some(Set("lucuma")),
            expiration = Some(inst.plusSeconds(jwtTimeout.toSeconds).getEpochSecond),
            notBefore  = Some(inst.getEpochSecond),
            issuedAt   = Some(inst.getEpochSecond),
          )
        }

      def newClaim(user: User): F[JwtClaim] =
        newClaim(
          content = Json.obj(lucumaUser -> user.asJson).spaces2,
          subject = Some(user.id.value.toString())
        )

      def renewedClaim(claim: JwtClaim): F[JwtClaim] =
        newClaim(claim.content, claim.subject)

      def newJwt(user: User): F[String] =
        newClaim(user).flatMap(jwtEncoder.encode)

    }

}