package gpp.sso.service.orcid

import io.circe.Decoder
import cats.effect.Sync
import org.http4s.EntityDecoder
import org.http4s.circe._

case class OrcidPerson(
  name:   OrcidName,
  emails: List[OrcidEmail]
)

object OrcidPerson {

  implicit val DecoderOrcidPerson: Decoder[OrcidPerson] = c =>
    for {
      n  <- c.downField("name").as[OrcidName]
      es <- c.downField("emails").downField("email").as[List[OrcidEmail]]
    } yield OrcidPerson(n, es)

  implicit def entityDecoderOrcidPerson[F[_]: Sync]: EntityDecoder[F, OrcidPerson] =
    jsonOf[F, OrcidPerson]

}