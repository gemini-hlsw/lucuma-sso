package gpp.sso.service

import gpp.sso.model.User
import pdi.jwt.JwtClaim
import scala.concurrent.duration.FiniteDuration
import java.time.Instant
import cats.effect.Sync
import cats.implicits._
import io.circe.syntax._
import io.circe.Json
import gpp.sso.client.Keys

trait JwtFactory[F[_]] {
  def newClaimForUser(user: User): F[JwtClaim]
  def renewedClaim(claim: JwtClaim): F[JwtClaim]
}

object JwtFactory {

  def withTimeout[F[_]: Sync](timeout: FiniteDuration): JwtFactory[F] =
    new JwtFactory[F] {

      val now: F[Instant] =
        Sync[F].delay(Instant.now)

      def newClaimForUser(user: User): F[JwtClaim] =
        now.map { inst =>
          JwtClaim(
            content    = Json.obj(Keys.GppUser -> user.asJson).spaces2,
            issuer     = Some("gpp-sso"),
            subject    = Some(user.id.value.toString()),
            audience   = Some(Set("gpp")),
            expiration = Some(inst.plusSeconds(timeout.toSeconds).getEpochSecond),
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
            expiration = Some(inst.plusSeconds(timeout.toSeconds).getEpochSecond),
            notBefore  = Some(inst.getEpochSecond),
            issuedAt   = Some(inst.getEpochSecond),
          )
        }

    }



}