// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.example

import cats._
import cats.effect._
import lucuma.core.model.User
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.CORS
import org.http4s.server.Server
import lucuma.sso.client.SsoClient

object Main extends IOApp {

  // A normal server.
  def serverResource[F[_]: Concurrent: ContextShift: Timer](
    port: Int,
    app:  HttpApp[F]
  ): Resource[F, Server[F]] =
    EmberServerBuilder
      .default[F]
      .withHost("0.0.0.0")
      .withHttpApp(app)
      .withPort(port)
      .build

  // Our routes use an `SsoClient` that knows how to extract a `User`.
  def routes[F[_]: Defer: Applicative](
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

  // Our main program as a resource.
  def runR[F[_]: Concurrent: Timer: ContextShift](
    cfg: Config
  ): Resource[F, Server[F]] =
    for {
      ssoClient <- cfg.ssoClient[F]
      userClient = ssoClient.map(_.user) // we only want part of the UserInfo
      httpApp    = CORS.httpRoutes(routes[F](userClient)).orNotFound
      server    <- serverResource(cfg.port, httpApp)
    } yield server

  // Specialize to IO and we're done.
  def run(args: List[String]): IO[ExitCode] =
    for {
      cfg <- Config.fromCiris.load[IO]
      _   <- runR[IO](cfg).use(_ => IO.never.void)
    } yield ExitCode.Success

}