package gpp.sso.service
package simulator

import cats.effect._
import scala.concurrent.duration._
import java.security.KeyPairGenerator
import java.security.SecureRandom
import gpp.sso.client.GppJwtDecoder
import gpp.sso.client.JwtDecoder
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.server.Router

/** An SSO server with simulated database and ORCID service. */
object SsoSimulator {

  val httpRoutes: HttpRoutes[IO] = {
    val keyGen  = KeyPairGenerator.getInstance("RSA", "SunRsaSign")
    val random  = SecureRandom.getInstance("SHA1PRNG", "SUN")
    val keyPair = { keyGen.initialize(1024, random); keyGen.generateKeyPair }
    FMain.routes[IO](
      pool       = DatabaseSimulator.pool,
      orcid      = OrcidSimulator.service,
      jwtDecoder = GppJwtDecoder.fromJwtDecoder(JwtDecoder.withPublicKey(keyPair.getPublic)),
      jwtEncoder = JwtEncoder.withPrivateKey(keyPair.getPrivate),
      jwtFactory = JwtFactory.withTimeout(10.minutes)
    )
  }

  val client: Client[IO] =
    Client.fromHttpApp(Router("/" -> httpRoutes).orNotFound)

}
