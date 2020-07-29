package gpp.sso.client

import gpp.sso.model.User

import cats.implicits._
import cats.MonadError
import io.circe.parser._

/** JWTs issued by SSO contain a User. */
trait GppJwtDecoder[F[_]] {
  def decode(token: String): F[User]
}

object GppJwtDecoder {

  def fromJwtDecoder[F[_]: MonadError[*[_], Throwable]](jwtDecoder: JwtDecoder[F]): GppJwtDecoder[F] =
    new GppJwtDecoder[F] {
      def decode(token: String): F[User] =
        for {
          claim <- jwtDecoder.decode(token)
          json  <- parse(claim.content).liftTo[F]
          user  <- json.hcursor.downField(Keys.GppUser).as[User].liftTo[F]
        } yield user
    }

}