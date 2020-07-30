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
import gpp.sso.service.orcid.OrcidService
import gpp.sso.service.config.Config
import natchez.Trace.Implicits.noop
import gpp.sso.service.database.Database

object SsoSimulator {

  // The exact same routes and database used by SSO, but a fake ORCID back end
  private def httpRoutes[F[_]: Concurrent: ContextShift]: Resource[F, (OrcidSimulator[F], HttpRoutes[F])] =
    Resource.liftF(OrcidSimulator[F]).flatMap { sim =>
    FMain.poolResource[F](Config.Local.database).map { pool =>
      val keyGen  = KeyPairGenerator.getInstance("RSA", "SunRsaSign")
      val random  = SecureRandom.getInstance("SHA1PRNG", "SUN")
      val keyPair = { keyGen.initialize(1024, random); keyGen.generateKeyPair }
      (sim, Routes[F](
        pool       = pool.map(Database.fromSession(_)),
        orcid      = OrcidService("unused", "unused", sim.client),
        jwtDecoder = GppJwtDecoder.fromJwtDecoder(JwtDecoder.withPublicKey(keyPair.getPublic)),
        jwtEncoder = JwtEncoder.withPrivateKey(keyPair.getPrivate),
        jwtFactory = JwtFactory.withTimeout(10.minutes)
      ))
    }
  }

  /** An Http client that hits an SSO server backed by a simulated ORCID server. */
  def apply[F[_]: Concurrent: ContextShift]: Resource[F, (OrcidSimulator[F], Client[F])] =
    httpRoutes[F].map { case (sim, routes) =>
      (sim, Client.fromHttpApp(Router("/" -> routes).orNotFound))
    }

}
