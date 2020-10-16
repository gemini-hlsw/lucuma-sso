package lucuma.sso.client

import pdi.jwt.JwtClaim
import cats.MonadError
import cats.syntax.all._
import lucuma.core.model.User
import lucuma.sso.client.codec.user._
import io.circe.parser.parse

final case class SsoJwtClaim(jwtClaim: JwtClaim) {
  import SsoJwtClaim._

  def getUserF[F[_]: MonadError[*[_], Throwable]]: F[User] =
    for {
      json  <- parse(jwtClaim.content).liftTo[F]
      user  <- json.hcursor.downField(lucumaUser).as[User].liftTo[F]
    } yield user

}

object SsoJwtClaim {
  private[sso] val lucumaUser = "lucuma-user"
}