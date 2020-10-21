package lucuma.sso.service
package simulator

import cats.effect._
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.server.Router
import lucuma.sso.service.orcid.OrcidService
import lucuma.sso.service.config.Config
import natchez.Trace.Implicits.noop
import lucuma.sso.service.database.Database
import lucuma.sso.client.SsoJwtReader
import io.chrisdavenport.log4cats.Logger
import lucuma.sso.service.config.OrcidConfig
import lucuma.sso.service.config.Environment

object SsoSimulator {

  // The exact same routes and database used by SSO, but a fake ORCID back end
  private def httpRoutes[F[_]: Concurrent: ContextShift: Timer: Logger]: Resource[F, (Resource[F, Database[F]], OrcidSimulator[F], HttpRoutes[F], SsoJwtReader[F])] =
    Resource.liftF(OrcidSimulator[F]).flatMap { sim =>
      val config = Config.local(null) // no ORCID config since we're faking ORCID
      FMain.databasePoolResource[F](config.database).map { pool =>

        val sessionPool = pool.map(Database.fromSession(_))

        (sessionPool, sim, Routes[F](
          dbPool    = sessionPool,
          orcid     = OrcidService(OrcidConfig.orcidHost(Environment.Production), "unused", "unused", sim.client),
          jwtWriter = config.ssoJwtWriter,
          publicUri = config.publicUri,
          cookies   = CookieService[F](Some("gemini.edu"))
        ), config.ssoJwtReader)
    }
  }

  /** An Http client that hits an SSO server backed by a simulated ORCID server. */
  def apply[F[_]: Concurrent: ContextShift: Timer: Logger]: Resource[F, (Resource[F, Database[F]], OrcidSimulator[F], Client[F], SsoJwtReader[F])] =
    httpRoutes[F].map { case (pool, sim, routes, reader) =>
      (pool, sim, Client.fromHttpApp(Router("/" -> routes).orNotFound), reader)
    }

}
