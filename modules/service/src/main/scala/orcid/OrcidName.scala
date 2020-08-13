package gpp.sso.service.orcid

import gpp.sso.model.OrcidProfile
import io.circe._
import io.circe.syntax._

case class OrcidName(
  familyName: Option[String],
  givenName:  Option[String],
  creditName: Option[String],
) {
  def displayName: Option[String] =
    OrcidProfile.displayName(givenName, familyName, creditName)
}
object OrcidName {

  implicit val DecoderOrcidName: Decoder[OrcidName] = c =>
    for {
      f <- c.downField("family-name").downField("value").as[Option[String]]
      g <- c.downField("given-names").downField("value").as[Option[String]]
      c <- c.downField("credit-name").downField("value").as[Option[String]]
    } yield OrcidName(f,g,c)

  implicit val EncoderOrcidName: Encoder[OrcidName] = n =>
    Json.obj(
      "family-name" -> Json.obj("value" -> n.familyName.asJson),
      "given-names" -> Json.obj("value" -> n.givenName.asJson),
      "credit-name" -> Json.obj("value" -> n.creditName.asJson),
    )

}