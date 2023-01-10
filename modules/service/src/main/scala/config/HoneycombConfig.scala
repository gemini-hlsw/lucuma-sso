// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.config

import cats.syntax.all._
import ciris._

case class HoneycombConfig(
  writeKey: String,
  dataset:  String,
)

object HoneycombConfig {

  val config: ConfigValue[Effect, HoneycombConfig] =
    (envOrProp("HONEYCOMB_WRITE_KEY"), envOrProp("HONEYCOMB_DATASET")).parMapN(HoneycombConfig(_, _))

}