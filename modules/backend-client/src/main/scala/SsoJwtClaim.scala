// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client

import pdi.jwt.JwtClaim
import lucuma.core.model.User
import lucuma.sso.client.codec.user._
import io.circe.parser.parse

final case class SsoJwtClaim(jwtClaim: JwtClaim) {
  import SsoJwtClaim._

  def getUser: Either[Throwable, User] =
    for {
      json  <- parse(jwtClaim.content)
      user  <- json.hcursor.downField(lucumaUser).as[User]
    } yield user

}

object SsoJwtClaim {
  private[sso] val lucumaUser = "lucuma-user"
}