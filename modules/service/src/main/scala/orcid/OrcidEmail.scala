// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.orcid

import io.circe._
import io.circe.generic.semiauto._

case class OrcidEmail(
  email:    String,
  verified: Boolean,
  primary:  Boolean,
)

object OrcidEmail {

  implicit def DecoderOrcidEmail: Decoder[OrcidEmail] =
    deriveDecoder

  implicit def EncoderOrcidEmail: Encoder[OrcidEmail] =
    deriveEncoder

}