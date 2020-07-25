package gpp.sso.service.config

import ciris._
import cats.implicits._

final case class OrcidConfig(
  clientId: String,
  clientSecret: String
)

object OrcidConfig {

  val Local = new OrcidConfig("APP-XCUB4VY7YAN9U6BH", "7600dc74-dfc9-4200-afb5-6306029279ae")

  val config: ConfigValue[OrcidConfig] = (
    env("GPP_ORCID_CLIENT_ID"),
    env("GPP_ORCID_CLIENT_SECRET")
  ).parMapN(apply(_, _))

}