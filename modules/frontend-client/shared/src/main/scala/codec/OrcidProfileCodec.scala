// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client.codec

import io.circe._
import io.circe.generic.semiauto._
import lucuma.core.model.OrcidProfile

trait OrcidProfileCodec {
  implicit val encoder: Encoder[OrcidProfile] = deriveEncoder
  implicit val decoder: Decoder[OrcidProfile] = deriveDecoder
}

object orcidProfile extends OrcidProfileCodec