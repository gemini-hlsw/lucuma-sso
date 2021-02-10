package lucuma.sso.service.config

import cats.syntax.all._
import ciris.ConfigValue

case class HoneycombConfig(
  writeKey: String,
  dataset:  String,
)

object HoneycombConfig {

  val config: ConfigValue[HoneycombConfig] =
    (envOrProp("HONEYCOMB_WRITE_KEY"), envOrProp("HONEYCOMB_DATASET")).parMapN(HoneycombConfig(_, _))

}