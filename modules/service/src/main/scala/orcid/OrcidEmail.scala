package gpp.sso.service.orcid

import io.circe.Decoder
import io.circe.generic.semiauto._

case class OrcidEmail(
  email:    String,
  verified: Boolean,
  primary:  Boolean,
)

object OrcidEmail {

  implicit def DecoderOrcidEmail: Decoder[OrcidEmail] =
    deriveDecoder

}