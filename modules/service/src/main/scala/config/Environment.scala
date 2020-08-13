package gpp.sso.service.config

import ciris._
import cats.implicits._

sealed trait Environment
object Environment {

  final case object Local      extends Environment
  final case object Test       extends Environment
  final case object Production extends Environment

  implicit val ConfigDecoderEnvironment: ConfigDecoder[String, Environment] =
    ConfigDecoder[String].map(_.toLowerCase).collect("Environment") {
      case "Local" => Local
      case "Test"  => Test
      case "Production" => Production
    }

}
