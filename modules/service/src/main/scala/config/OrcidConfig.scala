// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.service.config

import ciris._
import cats.implicits._

final case class OrcidConfig(
  clientId: String,
  clientSecret: String
)

object OrcidConfig {

  val Local = new OrcidConfig("APP-XCUB4VY7YAN9U6BH", "265b63e5-a924-4512-a1e8-573fcfefa92d")

  val config: ConfigValue[OrcidConfig] = (
    env("GPP_ORCID_CLIENT_ID"),
    env("GPP_ORCID_CLIENT_SECRET")
  ).parMapN(apply(_, _))

}