package gpp.sso.service.orcid

import io.circe.Decoder

case class OrcidName(
  familyName: Option[String],
  givenName:  Option[String],
  creditName: Option[String],
)

object OrcidName {

  implicit def DecoderOrcidName: Decoder[OrcidName] = c =>
    for {
      g <- c.downField("given-names").downField("value").as[Option[String]]
      f <- c.downField("family-name").downField("value").as[Option[String]]
      c <- c.downField("credit-name").downField("value").as[Option[String]]
    } yield OrcidName(g, f, c)

}