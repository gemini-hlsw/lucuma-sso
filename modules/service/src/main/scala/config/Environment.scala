// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.config

import ciris._
import cats.implicits._

sealed trait Environment
object Environment {

  final case object Local      extends Environment
  final case object Staging    extends Environment
  final case object Production extends Environment

  implicit val ConfigDecoderEnvironment: ConfigDecoder[String, Environment] =
    ConfigDecoder[String].map(_.toLowerCase).collect("Environment") {
      case "local" => Local
      case "staging"  => Staging
      case "production" => Production
    }

}
