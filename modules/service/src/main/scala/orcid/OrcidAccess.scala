package gpp.sso.service.orcid

import gpp.sso.model.Orcid
import io.circe.Decoder
import java.time.Duration
import java.util.UUID
import org.http4s.EntityDecoder
import org.http4s.circe._
import cats.effect.Sync

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

  implicit val DecoderAccessTokenInfo: Decoder[OrcidAccess] = c =>
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

}