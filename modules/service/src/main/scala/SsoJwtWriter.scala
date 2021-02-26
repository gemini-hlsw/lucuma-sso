// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import lucuma.core.model.User
import lucuma.sso.client.SsoJwtClaim
import pdi.jwt.JwtClaim
import java.time.Instant
import cats.effect.Sync
import cats.implicits._
import io.circe.Json
import io.circe.syntax._
import scala.concurrent.duration.FiniteDuration
import lucuma.sso.service.util.JwtEncoder
import lucuma.sso.client.codec.user._
import org.http4s.Request
import org.http4s.headers.Authorization
import org.http4s.Credentials
import org.http4s.util.CaseInsensitiveString

trait SsoJwtWriter[F[_]] {

  // Encoded JWTs
  def newJwt(user: User, timeout: Option[FiniteDuration] = None): F[String]

  def addAuthorizationHeader(user: User, req: Request[F]): F[Request[F]]

}

object SsoJwtWriter {

  private val lucumaUser   = SsoJwtClaim.lucumaUser

  val HttpOnly = true // JS can't see the cookie
  val SameSite = org.http4s.SameSite.None // We don't care

  def apply[F[_]: Sync](
    jwtEncoder: JwtEncoder[F],
    jwtTimeout: FiniteDuration,
  ): SsoJwtWriter[F] =
    new SsoJwtWriter[F] {

      val now: F[Instant] =
        Sync[F].delay(Instant.now)

      val Bearer = CaseInsensitiveString("Bearer")

      def newClaim(content: String, subject: Option[String], timeout: FiniteDuration): F[JwtClaim] =
        now.map { inst =>
          JwtClaim(
            content    = content,
            issuer     = Some("lucuma-sso"),
            subject    = subject,
            audience   = Some(Set("lucuma")),
            expiration = Some(inst.plusSeconds(timeout.toSeconds).getEpochSecond),
            notBefore  = Some(inst.getEpochSecond),
            issuedAt   = Some(inst.getEpochSecond),
          )
        }

      def newClaim(user: User, timeout: FiniteDuration): F[JwtClaim] =
        newClaim(
          content = Json.obj(lucumaUser -> user.asJson).spaces2,
          subject = Some(user.id.value.toString()),
          timeout = timeout,
        )

      def newJwt(user: User, timeout: Option[FiniteDuration]): F[String] =
        newClaim(user, timeout.getOrElse(jwtTimeout)).flatMap(jwtEncoder.encode)

      def addAuthorizationHeader(user: User, req: Request[F]): F[Request[F]] =
         newJwt(user, None).map(jwt => req.putHeaders(Authorization(Credentials.Token(Bearer, jwt))))

    }

}