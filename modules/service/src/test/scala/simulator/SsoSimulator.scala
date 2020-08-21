package gpp.sso.service
package simulator

import cats.effect._
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.server.Router
import gpp.sso.service.orcid.OrcidService
import gpp.sso.service.config.Config
import natchez.Trace.Implicits.noop
import gpp.sso.service.database.Database
import gpp.sso.client.SsoCookieReader
import org.http4s.Uri
import org.http4s.Uri.RegName
import io.chrisdavenport.log4cats.Logger

object SsoSimulator {

  // The exact same routes and database used by SSO, but a fake ORCID back end
  private def httpRoutes[F[_]: Concurrent: ContextShift: Timer: Logger]: Resource[F, (OrcidSimulator[F], HttpRoutes[F], SsoCookieReader[F])] =
    Resource.liftF(OrcidSimulator[F]).flatMap { sim =>
    FMain.databasePoolResource[F](Config.Local.database).map { pool =>
      (sim, Routes[F](
        dbPool       = pool.map(Database.fromSession(_)),
        orcid        = OrcidService("unused", "unused", sim.client),
        cookieReader = Config.Local.cookieReader,
        cookieWriter = Config.Local.cookieWriter,
        scheme       = Uri.Scheme.https,
        authority    = Uri.Authority(
          host = RegName("sso.gemini.edu"),
          port = Some(80),
        )
      ), Config.Local.cookieReader)
    }
  }

  /** An Http client that hits an SSO server backed by a simulated ORCID server. */
  def apply[F[_]: Concurrent: ContextShift: Timer: Logger]: Resource[F, (OrcidSimulator[F], Client[F], SsoCookieReader[F])] =
    httpRoutes[F].map { case (sim, routes, reader) =>
      (sim, Client.fromHttpApp(Router("/" -> routes).orNotFound), reader)
    }

}
