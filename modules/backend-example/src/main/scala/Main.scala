// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.example

import cats._
import cats.effect._
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import lucuma.core.model.User
import lucuma.sso.client.SsoClient
import lucuma.sso.client.SsoMiddleware
import natchez.EntryPoint
import natchez.Trace
import natchez.honeycomb.Honeycomb
import natchez.http4s.NatchezMiddleware
import natchez.http4s.implicits._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.ErrorAction

import scala.annotation.unused
import scala.concurrent.duration._

object Main extends IOApp {

  val host: Host =
    Host.fromString("0.0.0.0").getOrElse(sys.error("unpossible: invalid host"))

  // A normal server.
  def serverResource[F[_]: Async](
    port: Port,
    app:  HttpApp[F]
  ): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(host)
      .withHttpApp(app)
      .withPort(port)
      .build

  // Our routes use an `SsoClient` that knows how to extract a `User`.
  def routes[F[_]: Monad](
    userClient: SsoClient[F, User]
  ): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]; import dsl._
    HttpRoutes.of[F] {
      case r@(GET -> Root / "echo" / greeting) =>
        userClient.require(r) { user =>
          Ok(s"$greeting ${user.displayName}")
        } // otherwise we will respond 403 Forbidden
    }
  }

  // Our routes, with middleware
  def wrappedRoutes[F[_]: Async: Trace](
    cfg: Config
  ): Resource[F, HttpRoutes[F]] =
    cfg.ssoClient[F].map { ssoClient =>
      val userClient = ssoClient.map(_.user) // we only want part of the UserInfo
      NatchezMiddleware.server(SsoMiddleware(userClient)(routes[F](userClient)))
    }

  def entryPoint[F[_]: Sync](cfg: Config): Resource[F, EntryPoint[F]] =
    Honeycomb.entryPoint("backend-example") { cb =>
      Sync[F].delay {
        cb.setWriteKey(cfg.hcWriteKey)
        cb.setDataset(cfg.hcDataset)
        cb.build()
      }
    }

  def log[F[_]: Async](@unused r: Request[F], t: Throwable): F[Unit] =
    Async[F].delay(t.printStackTrace())

  def cors[F[_]: Monad](routes: HttpRoutes[F]): HttpRoutes[F] =
    CORS.policy
      .withAllowCredentials(true)
      .withMaxAge(1.day)
      .apply(routes)

  // Our main program as a resource.
  def runR[F[_]: Async](
    cfg: Config
  ): Resource[F, Server] =
    for {
      ep      <- entryPoint[F](cfg)
      routes  <- ep.liftR(wrappedRoutes(cfg))
      httpApp  = ErrorAction.httpRoutes(cors(routes), log[F]).orNotFound
      server  <- serverResource(cfg.port, httpApp)
    } yield server

  // Specialize to IO and we're done.
  def run(args: List[String]): IO[ExitCode] =
    for {
      cfg <- Config.fromCiris.load[IO]
      _   <- runR[IO](cfg).use(_ => IO.never.void)
    } yield ExitCode.Success

}