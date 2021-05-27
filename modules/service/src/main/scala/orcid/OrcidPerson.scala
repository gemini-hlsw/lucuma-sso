// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.orcid

import io.circe._
import io.circe.syntax._
import cats.effect.Concurrent
import org.http4s.{ EntityDecoder, EntityEncoder }
import org.http4s.circe._

case class OrcidPerson(
  name:   OrcidName,
  emails: List[OrcidEmail]
) {

  def primaryEmail: Option[OrcidEmail] =
    emails.find(_.primary)

}

object OrcidPerson {

  implicit val DecoderOrcidPerson: Decoder[OrcidPerson] = c =>
    for {
      n  <- c.downField("name").as[Option[OrcidName]].map(_.getOrElse(OrcidName(None, None, None)))
      es <- c.downField("emails").downField("email").as[List[OrcidEmail]]
    } yield OrcidPerson(n, es)

  implicit def entityDecoderOrcidPerson[F[_]: Concurrent]: EntityDecoder[F, OrcidPerson] =
    jsonOf[F, OrcidPerson]

  implicit val EncoderOrcidPerson: Encoder[OrcidPerson] = p =>
    Json.obj(
      "name"   -> p.name.asJson,
      "emails" -> Json.obj("email" -> p.emails.asJson)
    )

  implicit def entityEncoderOrcidPerson[F[_]]: EntityEncoder[F, OrcidPerson] =
    EntityEncoder[F, Json].contramap(_.asJson)

}