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