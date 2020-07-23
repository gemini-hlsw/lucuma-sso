package gpp.sso

import cats.effect._
import cats._
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.implicits._
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import skunk.Session
import natchez.Trace
import cats.Monad
import gpp.sso.service.database.Database
import org.http4s.Response
import org.http4s.Status
import org.http4s.EntityEncoder
import java.io.StringWriter
import java.io.PrintWriter
import natchez.EntryPoint
import natchez.jaeger.Jaeger
import io.jaegertracing.Configuration.SamplerConfiguration
import io.jaegertracing.Configuration.ReporterConfiguration
import natchez.http4s.implicits._
import gpp.ssp.service.config.DatabaseConfig
import gpp.sso.service.config.Config

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    FMain.main[IO]
}

object FMain {

  val MaxConnections = 10 // max db connections

  def poolResource[F[_]: Concurrent: ContextShift: Trace](config: DatabaseConfig): Resource[F, Resource[F, Session[F]]] =
    Session.pooled(
      host     = config.host,
      port     = config.port,
      user     = config.user,
      database = config.database,
      max      = MaxConnections,
    )

  def routes[F[_]: Concurrent: Trace](
    pool: Resource[F, Database[F]]
  ): HttpRoutes[F] = {
    object FDsl extends Http4sDsl[F]
    import FDsl._
    HttpRoutes.of[F] {

      // API route that
      case POST -> Root / "api" / "v1" / "authAsGuest" =>
        pool.use { db =>
          for {
            gu <- db.createGuestUser
            r  <- Ok(s"created $gu")
          } yield r.addCookie("X-GPP-SSO-JWT", "woozle")
        }

    }
  }

  def app[F[_]: Monad](routes: HttpRoutes[F]): HttpApp[F] =
    Router("/" -> routes).orNotFound

  def server[F[_]: Concurrent: ContextShift: Timer](
    port: Int,
    app:  HttpApp[F]
  ): Resource[F, Server[F]] =
    EmberServerBuilder
      .default[F]
      .withHost("0.0.0.0")
      .withHttpApp(app)
      .withPort(port)
      .withOnError { t =>
        // for now let's include the stacktrace in the message body
        val sw = new StringWriter
        t.printStackTrace(new PrintWriter(sw))
        Response[F](
          status = Status.InternalServerError,
          body   = EntityEncoder[F, String].toEntity(sw.toString).body
        )
      }
      .build

  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    Jaeger.entryPoint[F]("gpp-sso") { c =>
      Sync[F].delay {
        c.withSampler(SamplerConfiguration.fromEnv)
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
  }

  def routesResource[F[_]: Concurrent: ContextShift: Trace](config: DatabaseConfig) =
    poolResource[F](config)
      .map(p => routes(p.map(Database.fromSession(_))))
      .map(natchezMiddleware(_))

  def rmain[F[_]: Concurrent: ContextShift: Timer]: Resource[F, ExitCode] =
    for {
      c  <- Resource.liftF(Config.config.load[F])
      ep <- entryPoint
      rs <- ep.liftR(routesResource(c.database))
      ap  = app(rs)
      _  <- server(8080, ap)
    } yield ExitCode.Success

  def main[F[_]: Concurrent: ContextShift: Timer]: F[ExitCode] =
    rmain.use(_ => Concurrent[F].never[ExitCode])

}

