package gpp.sso.service.orcid

import io.circe.Decoder
import gpp.sso.model.OrcidProfile

case class OrcidName(
  familyName: Option[String],
  givenName:  Option[String],
  creditName: Option[String],
) {
  def displayName: Option[String] =
    OrcidProfile.displayName(givenName, familyName, creditName)
}
object OrcidName {

  implicit def DecoderOrcidName: Decoder[OrcidName] = c =>
    for {
      g <- c.downField("given-names").downField("value").as[Option[String]]
      f <- c.downField("family-name").downField("value").as[Option[String]]
      c <- c.downField("credit-name").downField("value").as[Option[String]]
    } yield OrcidName(g, f, c)

}