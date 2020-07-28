package gpp.sso.service.orcid

import io.circe._
import io.circe.syntax._
import cats.effect.Sync
import org.http4s.{ EntityDecoder, EntityEncoder }
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

  implicit val EncoderOrcidPerson: Encoder[OrcidPerson] = p =>
    Json.obj(
      "name"   -> p.name.asJson,
      "emails" -> Json.obj("email" -> p.emails.asJson)
    )

  implicit def entityEncoderOrcidPerson[F[_]: Sync]: EntityEncoder[F, OrcidPerson] =
    EntityEncoder[F, Json].contramap(_.asJson)

}