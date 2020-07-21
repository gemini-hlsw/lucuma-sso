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

object Main extends IOApp {
  import natchez.Trace.Implicits.noop
  def run(args: List[String]): IO[ExitCode] =
    FMain.main[IO]
}

object FMain {

  def poolResource[F[_]: Concurrent: ContextShift: Trace]: Resource[F, Resource[F, Session[F]]] =
    Session.pooled(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "gpp-sso",
      max      = 10,
    )

  def routes[F[_]: Concurrent: Trace](pool: Resource[F, Database[F]]): HttpRoutes[F] = {
    object FDsl extends Http4sDsl[F]
    import FDsl._
    HttpRoutes.of[F] {
      case GET -> Root / "api" / "v1" / "authAsGuest" =>
        pool.use(_.createGuestUser.flatMap(u => Ok(s"created $u")))
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

  def rmain[F[_]: Concurrent: ContextShift: Trace: Timer]: Resource[F, ExitCode] =
    for {
      p  <- poolResource
      d   = p.map(Database.fromSession(_))
      rs  = routes(d)
      ap  = app(rs)
      _  <- server(8080, ap)
    } yield ExitCode.Success

  def main[F[_]: Concurrent: ContextShift: Trace: Timer]: F[ExitCode] =
    rmain.use(_ => Concurrent[F].never[ExitCode])

}

