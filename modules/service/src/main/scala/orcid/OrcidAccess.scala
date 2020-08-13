package gpp.sso.service.orcid

import gpp.sso.model.Orcid
import io.circe.Decoder
import io.circe.syntax._
import java.time.Duration
import java.util.UUID
import org.http4s.EntityDecoder
import org.http4s.circe._
import cats.effect.Sync
import io.circe.Encoder
import io.circe.Json
import org.http4s.EntityEncoder

final case class OrcidAccess(
  accessToken:  UUID,
  tokenType:    String,
  refreshToken: UUID,
  expiresIn:    Duration,
  scope:        String,
  name:         String,
  orcidId:      Orcid
)

object OrcidAccess {

  implicit val DecoderOrcidId: Decoder[Orcid] =
    Decoder[String].emap(s => Orcid.fromString(s).toRight(s"Invalid ORCID iD: $s"))

  implicit val DecoderOrcidAccess: Decoder[OrcidAccess] = c =>
    for {
      a <- c.downField("access_token").as[UUID]
      t <- c.downField("token_type").as[String]
      r <- c.downField("refresh_token").as[UUID]
      e <- c.downField("expires_in").as[Long].map(Duration.ofSeconds)
      s <- c.downField("scope").as[String]
      n <- c.downField("name").as[String]
      o <- c.downField("orcid").as[Orcid]
    } yield OrcidAccess(a, t, r, e, s, n, o)

  implicit def entityDecoderOrcidAccess[F[_]: Sync]: EntityDecoder[F, OrcidAccess] =
    jsonOf[F, OrcidAccess]

  implicit val EncoderOrcidId: Encoder[Orcid] =
    Encoder[String].contramap(_.value)

  implicit val EncoderOrcidAccess: Encoder[OrcidAccess] = a =>
    Json.obj(
      ("access_token"  -> a.accessToken.asJson),
      ("token_type"    -> a.tokenType.asJson),
      ("refresh_token" -> a.refreshToken.asJson),
      ("expires_in"    -> a.expiresIn.getSeconds.asJson),
      ("scope"         -> a.scope.asJson),
      ("name"          -> a.name.asJson),
      ("orcid"         -> a.orcidId.asJson),
    )

  implicit def entityEncoderOrcidAccess[F[_]: Sync]: EntityEncoder[F, OrcidAccess] =
    EntityEncoder[F, Json].contramap(_.asJson)

}