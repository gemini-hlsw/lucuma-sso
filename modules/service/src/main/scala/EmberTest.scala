package test

import cats.effect._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.HttpApp
import java.io.StringWriter
import java.io.PrintWriter
import org.http4s.Status
import org.http4s.EntityEncoder

object EmberTest extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withPort(8080)
      .withHttpApp(HttpApp.notFound)
      .withOnError { t =>
        val sw = new StringWriter
        t.printStackTrace()
        t.printStackTrace(new PrintWriter(sw))
        org.http4s.Response[IO](
          status = Status.InternalServerError,
          body   = EntityEncoder[IO, String].toEntity(sw.toString).body
        )
      }
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)

}