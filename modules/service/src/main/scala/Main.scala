// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats._
import cats.effect._
import cats.implicits._
import lucuma.sso.service.config._
import lucuma.sso.service.database.Database
import lucuma.sso.service.orcid.OrcidService
import io.jaegertracing.Configuration.{ ReporterConfiguration, SamplerConfiguration }
import natchez.{ EntryPoint, Trace }
import natchez.http4s.implicits._
import natchez.jaeger.Jaeger
import org.flywaydb.core.Flyway
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server._
import skunk._
import io.chrisdavenport.log4cats.Logger
import cats.data.Kleisli
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import natchez.http4s.AnsiFilterStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

object Main extends IOApp {

  /** A logger we can use everywhere. */
  implicit val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName("lucuma-sso")

  /** Our main program, which runs forever, and can be stopped with ^C. */
  def run(args: List[String]): IO[ExitCode] =
    FMain.main[IO]

}

object FMain {

  // TODO: put this in the config
  val MaxConnections = 10

  /** A resource that yields a Skunk session pool. */
  def databasePoolResource[F[_]: Concurrent: ContextShift: Trace](
    config: DatabaseConfig
  ): Resource[F, Resource[F, Session[F]]] =
    Session.pooled(
      host     = config.host,
      port     = config.port,
      user     = config.user,
      password = config.password,
      database = config.database,
      ssl      = SSL.Trusted.withFallback(true),
      max      = MaxConnections,
      strategy = Strategy.SearchPath,
      // debug    = true
    )


  /** A resource that yields a running HTTP server. */
  def serverResource[F[_]: Concurrent: ContextShift: Timer](
    port: Int,
    app:  HttpApp[F]
  ): Resource[F, Server[F]] =
    EmberServerBuilder
      .default[F]
      .withHost("0.0.0.0")
      .withHttpApp(app)
      .withPort(port)
      .withOnError { t =>
        // TODO: don't show this in production
        val baos = new ByteArrayOutputStream
        val fs   = new AnsiFilterStream(baos)
        val osw  = new OutputStreamWriter(fs, "UTF-8")
        val pw   = new PrintWriter(osw)
        t.printStackTrace(pw)
        pw.close()
        osw.close()
        fs.close()
        baos.close()
        Response[F](
          status = Status.InternalServerError,
        ).withEntity(new String(baos.toByteArray, "UTF-8"))
      }
      .build

  /** A resource that yields a Natchez tracing entry point. */
  def entryPointResource[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    Jaeger.entryPoint[F]("lucuma-sso") { c =>
      Sync[F].delay {
        c.withSampler(SamplerConfiguration.fromEnv)
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
  }

  /** A resource that yields an OrcidService. */
  def orcidServiceResource[F[_]: Concurrent: Timer: ContextShift](config: OrcidConfig) =
    EmberClientBuilder.default[F].build.map(org.http4s.client.middleware.Logger[F](
      logHeaders = true,
      logBody    = true,
    )).map { client =>
      OrcidService(config.orcidHost, config.clientId, config.clientSecret, client)
    }

  /** A resource that yields our HttpRoutes, wrapped in accessory middleware. */
  def routesResource[F[_]: Concurrent: ContextShift: Trace: Timer: Logger](config: Config): Resource[F, HttpRoutes[F]] =
    (databasePoolResource[F](config.database), orcidServiceResource(config.orcid))
      .mapN { (pool, orcid) =>
        Routes[F](
          dbPool       = pool.map(Database.fromSession(_)),
          orcid        = orcid,
          publicKey    = config.publicKey,
          cookieReader = config.cookieReader,
          cookieWriter = config.cookieWriter,
          publicUri    = config.publicUri,
        )
      } .map(ServerMiddleware(config))

  /** A startup action that prints a banner. */
  def banner[F[_]: Applicative: Logger](config: Config): F[Unit] = {
    val banner =
        s"""|
            |╦  ╦ ╦╔═╗╦ ╦╔╦╗╔═╗   ╔═╗╔═╗╔═╗
            |║  ║ ║║  ║ ║║║║╠═╣───╚═╗╚═╗║ ║
            |╩═╝╚═╝╚═╝╚═╝╩ ╩╩ ╩   ╚═╝╚═╝╚═╝
            |${config.versionText}
            |${config.environment} Environment at ${config.publicUri}
            |
            |Cookie domain is ${config.cookieDomain.getOrElse("<none>")}
            |ORCID host is ${config.orcid.orcidHost}
            |
            |""".stripMargin
    banner.linesIterator.toList.traverse_(Logger[F].info(_))
  }

  /** A startup action that runs database migrations using Flyway. */
  def migrateDatabase[F[_]: Sync](config: DatabaseConfig): F[Int] =
    Sync[F].delay {
      Flyway
        .configure()
        .dataSource(config.jdbcUrl, config.user, config.password.orEmpty)
        .baselineOnMigrate(true)
        .load()
        .migrate()
    }

  implicit def kleisliLogger[F[_]: Logger, A]: Logger[Kleisli[F, A, *]] =
    Logger[F].mapK(Kleisli.liftK)

  /**
   * Our main program, as a resource that starts up our server on acquire and shuts it all down
   * in cleanup, yielding an `ExitCode`. Users will `use` this resource and hold it forever.
   */
  def rmain[F[_]: Concurrent: ContextShift: Timer: Logger]: Resource[F, ExitCode] =
    for {
      c  <- Resource.liftF(Config.config.load[F])
      _  <- Resource.liftF(banner[F](c))
      _  <- Resource.liftF(migrateDatabase[F](c.database))
      ep <- entryPointResource
      ap <- ep.liftR(routesResource(c)).map(_.orNotFound)
      _  <- serverResource(c.httpPort, ap)
    } yield ExitCode.Success

  /** Our main program, which runs forever. */
  def main[F[_]: Concurrent: ContextShift: Timer: Logger]: F[ExitCode] =
    rmain.use(_ => Concurrent[F].never[ExitCode])

}

