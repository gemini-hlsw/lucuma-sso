// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.config

import ciris._
import cats.implicits._

final case class OrcidConfig(
  clientId: String,
  clientSecret: String
)

object OrcidConfig {

  // We can't fake this part for running locally, you really do need to have ORCID credentials. See
  // the project README for information on setting this up.

  val config: ConfigValue[OrcidConfig] = (
    envOrProp("LUCUMA_ORCID_CLIENT_ID"),
    envOrProp("LUCUMA_ORCID_CLIENT_SECRET")
  ).parMapN(apply(_, _))

}