// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client.codec

import io.circe.*
import io.circe.generic.semiauto.*
import lucuma.core.model.UserProfile

trait UserProfileCodec:
  given Encoder[UserProfile] = deriveEncoder
  given Decoder[UserProfile] = deriveDecoder

object userProfile extends UserProfileCodec