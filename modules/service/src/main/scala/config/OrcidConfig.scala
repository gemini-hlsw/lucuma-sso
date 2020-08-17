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

  val Local = new OrcidConfig("...", "...")

  val config: ConfigValue[OrcidConfig] = (
    env("GPP_ORCID_CLIENT_ID"),
    env("GPP_ORCID_CLIENT_SECRET")
  ).parMapN(apply(_, _))

}