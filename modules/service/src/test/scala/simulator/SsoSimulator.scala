// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service
package simulator

import cats.effect._
import cats.effect.std.Console
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.server.Router
import lucuma.sso.service.orcid.OrcidService
import lucuma.sso.service.config.Config
import natchez.Trace.Implicits.noop
import lucuma.sso.service.database.Database
import lucuma.sso.client.SsoJwtReader
import org.typelevel.log4cats.Logger
import lucuma.sso.service.config.OrcidConfig
import lucuma.sso.service.config.Environment
import org.http4s.client.middleware.CookieJar
import org.http4s.Uri
import edu.gemini.grackle.skunk.SkunkMonitor
import lucuma.sso.service.graphql.GraphQLRoutes
import cats.syntax.all._
import lucuma.core.model.StandardUser

object SsoSimulator {

  // The exact same routes and database used by SSO, but a fake ORCID back end
  private def httpRoutes[F[_]: Async: Console: Logger]: Resource[F, (Resource[F, Database[F]], OrcidSimulator[F], HttpRoutes[F], SsoJwtReader[F], SsoJwtWriter[F])] =
    for {
      sim     <- Resource.eval(OrcidSimulator[F])
      config   = Config.local(null, None).copy(scheme = Uri.Scheme.https) // no ORCID config since we're faking ORCID
      pool    <- FMain.databasePoolResource[F](config.database)
      dbPool   = pool.map(Database.fromSession(_))
    } yield (dbPool, sim, Routes[F](
        dbPool    = dbPool,
        orcid     = OrcidService(OrcidConfig.orcidHost(Environment.Production), "unused", "unused", sim.client),
        jwtReader = config.ssoJwtReader,
        jwtWriter = config.ssoJwtWriter,
        publicUri = config.publicUri,
        cookies   = CookieService[F]("lucuma.xyz", true),
      ) <+> GraphQLRoutes(
        LocalSsoClient(config.ssoJwtReader, dbPool).collect { case su: StandardUser => su },
        pool,
        SkunkMonitor.noopMonitor[F]
      ), config.ssoJwtReader, config.ssoJwtWriter)

  /** An Http client that hits an SSO server backed by a simulated ORCID server. */
  def apply[F[_]: Async: Logger: Console]: Resource[F, (Resource[F, Database[F]], OrcidSimulator[F], Client[F], SsoJwtReader[F], SsoJwtWriter[F])] = {
    httpRoutes[F].flatMap { case (pool, sim, routes, reader, writer) =>
      val client = Client.fromHttpApp(Router("/" -> routes).orNotFound)
      val clientʹ = Client[F] { req =>
        for {
          _   <- Resource.eval(Logger[F].debug(s"""Request(method=${req.method}, uri=${req.uri}, headers=${req.headers})"""))
          res <- client.run(req)
          _   <- Resource.eval(Logger[F].debug(s"""Response(status=${res.status}, headers=${res.headers})"""))
        } yield res
      }
      Resource.eval(CookieJar.impl[F](clientʹ)).map { client =>
        (pool, sim, client, reader, writer)
      }
    }
  }

}

