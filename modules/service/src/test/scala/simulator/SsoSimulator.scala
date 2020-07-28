package gpp.sso.service
package simulator

import cats.effect._
import cats.implicits._
import scala.concurrent.duration._
import java.security.KeyPairGenerator
import java.security.SecureRandom
import gpp.sso.client.GppJwtDecoder
import gpp.sso.client.JwtDecoder
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.server.Router
import gpp.sso.service.orcid.OrcidService

/** An SSO server with simulated database and ORCID back end. */
object SsoSimulator {

  // The exact same routes used by SSO, but with a fake database and ORCID back end
  private def httpRoutes[F[_]: Sync](sim: OrcidSimulator[F]): F[HttpRoutes[F]] =
    DatabaseSimulator.pool[F].map { pool =>
      val keyGen  = KeyPairGenerator.getInstance("RSA", "SunRsaSign")
      val random  = SecureRandom.getInstance("SHA1PRNG", "SUN")
      val keyPair = { keyGen.initialize(1024, random); keyGen.generateKeyPair }
      FMain.routes[F](
        pool       = pool,
        orcid      = OrcidService("unused", "unused", sim.client),
        jwtDecoder = GppJwtDecoder.fromJwtDecoder(JwtDecoder.withPublicKey(keyPair.getPublic)),
        jwtEncoder = JwtEncoder.withPrivateKey(keyPair.getPrivate),
        jwtFactory = JwtFactory.withTimeout(10.minutes)
      )
    }

  def apply[F[_]: Sync](sim: OrcidSimulator[F]): F[Client[F]] =
    httpRoutes[F](sim).map(routes => Client.fromHttpApp(Router("/" -> routes).orNotFound))

}
