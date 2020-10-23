// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.example

import cats._
import cats.effect._
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import java.security.PublicKey
import lucuma.core.model.User
import lucuma.sso.client.SsoMiddleware
import lucuma.sso.client.util.GpgPublicKeyReader
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.Logger.{ httpRoutes => log }
import org.http4s.server.Server

object Main extends IOApp {

  implicit val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName("lucuma-sso-example")

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

  def routes[F[_]: Defer: Applicative]: AuthedRoutes[User, F] = {
    object dsl extends Http4sDsl[F]; import dsl._
    AuthedRoutes.of[User, F] {
      case GET -> Root / "echo" / greeting as user =>
        Ok(s"$greeting ${user.displayName}")
    }
  }

  def fetchSsoPublicKey[F[_]: Concurrent: Timer: ContextShift](ssoRoot: Uri): F[PublicKey] =
    EmberClientBuilder.default[F].build.use { client =>
      implicit val decoder = GpgPublicKeyReader.entityDecoder[F]
      client.expect[PublicKey](ssoRoot / "api" / "v1" / "public-key")
    }

  def serverMiddleware[F[_]: Concurrent: Logger](
    pubKey: PublicKey)
  : AuthedRoutes[User, F] => HttpRoutes[F] =
    SsoMiddleware(pubKey) andThen // this adds a check for the Authorization header
    CORS.httpRoutes[F]    andThen // this is needed by some browsers
    log[F](                       // log what we're doing
      logHeaders        = true,
      logBody           = false,
      redactHeadersWhen = _ => false
    )

  def runF[F[_]: Concurrent: Timer: ContextShift: Logger](cfg: Config) =
    for {
      pub <- fetchSsoPublicKey(cfg.ssoRoot)
      app  = serverMiddleware(pub).apply(routes[F]).orNotFound
      a   <- serverResource(cfg.port, app).use(_ => Concurrent[F].never[ExitCode])
    } yield a

  def run(args: List[String]): IO[ExitCode] = {
    val cfg = Config.Local
    IO(println(s"My URI is ${cfg.uri}"))     *>
    IO(println(s"   SSO is ${cfg.ssoRoot}")) *>
    runF[IO](cfg)
  }

}