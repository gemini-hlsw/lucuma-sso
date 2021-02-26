// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats._
import cats.effect._
import cats.implicits._
import lucuma.sso.service.config._
import lucuma.sso.service.database.Database
import lucuma.sso.service.orcid.OrcidService
import natchez.{ EntryPoint, Trace }
import natchez.http4s.implicits._
import natchez.honeycomb.Honeycomb
import natchez.log.Log
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server._
import skunk.{ Command => _, _ }
import org.typelevel.log4cats.Logger
import cats.data.Kleisli
import org.typelevel.log4cats.slf4j.Slf4jLogger
import natchez.http4s.AnsiFilterStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import org.http4s.Uri.Scheme
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline._
import eu.timepit.refined.auto._
import scala.concurrent.duration._

object Main extends CommandIOApp(
  name    = "lucuma-sso",
  header  =
    s"""|╦  ╦ ╦╔═╗╦ ╦╔╦╗╔═╗   ╔═╗╔═╗╔═╗
        |║  ║ ║║  ║ ║║║║╠═╣───╚═╗╚═╗║ ║
        |╩═╝╚═╝╚═╝╚═╝╩ ╩╩ ╩   ╚═╝╚═╝╚═╝
        |
        |This is the Lucuma SSO service, which also has a few utility commands that must be invoked
        |here because they cannot appear in the public API.
        |
        |Configuration is entirely by environment variable or by Java system property.
        |""".stripMargin,
) {

  def main: Opts[IO[ExitCode]] =
    command

  implicit val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName("lucuma-sso")

  lazy val serve =
    Command(
      name   = "serve",
      header = "Run the SSO service.",
    )(FMain.serve[IO].pure[Opts])

  lazy val createServiceUser =
    Command(
      name   = "create-service-user",
      header = "Create a new service user."
    )(Opts.argument[String](metavar = "name").map(FMain.createServiceUser[IO](_)))

  lazy val command =
    Opts.subcommands(
      serve,
      createServiceUser
    )

}

object FMain {

  // TODO: put this in the config
  val MaxConnections = 10

  // The name we're know by in the tracing back end.
  val ServiceName = "lucuma-sso"

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
      .withErrorHandler { t =>
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
        ).withEntity(new String(baos.toByteArray, "UTF-8")).pure[F]
      }
      .build

  /**
   * A resource that yields a Natchez tracing entry point, either a Honeycomb endpoint if `config`
   * is defined, otherwise a log endpoint.
   */
  def entryPointResource[F[_]: Sync: Logger](config: Option[HoneycombConfig]): Resource[F, EntryPoint[F]] =
    config.fold(Log.entryPoint(ServiceName).pure[Resource[F, *]]) { cfg =>
      Honeycomb.entryPoint(ServiceName) { cb =>
          Sync[F].delay {
            cb.setWriteKey(cfg.writeKey)
            cb.setDataset(cfg.dataset)
            cb.build()
          }
        }
    }

  /** A resource that yields an OrcidService. */
  def orcidServiceResource[F[_]: Concurrent: Timer: ContextShift: Trace](config: OrcidConfig) =
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
        val dbPool = pool.map(Database.fromSession(_))
        Routes[F](
          dbPool    = dbPool,
          orcid     = orcid,
          jwtReader = config.ssoJwtReader,
          jwtWriter = config.ssoJwtWriter,
          publicUri = config.publicUri,
          cookies   = CookieService[F](config.cookieDomain, config.scheme === Scheme.https),
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
            |Cookie domain is ${config.cookieDomain}
            |ORCID host is ${config.orcid.orcidHost}
            |
            |""".stripMargin
    banner.linesIterator.toList.traverse_(Logger[F].info(_))
  }

  /** A startup action that runs database migrations using Flyway. */
  def migrateDatabase[F[_]: Sync](config: DatabaseConfig): F[MigrateResult] =
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
   * Our main server, as a resource that starts up our server on acquire and shuts it all down
   * in cleanup, yielding an `ExitCode`. Users will `use` this resource and hold it forever.
   */
  def server[F[_]: Concurrent: ContextShift: Timer: Logger]: Resource[F, ExitCode] =
    for {
      c  <- Resource.liftF(Config.config.load[F])
      _  <- Resource.liftF(banner[F](c))
      _  <- Resource.liftF(migrateDatabase[F](c.database))
      ep <- entryPointResource(c.honeycomb)
      ap <- ep.liftR(routesResource(c)).map(_.orNotFound)
      _  <- serverResource(c.httpPort, ap)
    } yield ExitCode.Success

  /** Our main server, which runs forever. */
  def serve[F[_]: Concurrent: ContextShift: Timer: Logger]: F[ExitCode] =
    server.use(_ => Concurrent[F].never[ExitCode])

  /** Standalone single-use database instance for one-off commands. */
  def standaloneDatabase[F[_]: Concurrent: ContextShift](
    config: DatabaseConfig
  ): Resource[F, Database[F]] = {
    import Trace.Implicits.noop
    databasePoolResource(config).flatten.map(Database.fromSession(_))
  }

  /** A one-off command that creates a service user. */
  def createServiceUser[F[_]: Concurrent: ContextShift](
    name: String
  ): F[ExitCode] =
    Config.config.load[F].flatMap { config =>
      standaloneDatabase(config.database).use { db =>
        for {
          user <- db.canonicalizeServiceUser(name)
          _    <- Sync[F].delay(println())
          _    <- Sync[F].delay(println(s"⚠️  JWT for service user '${user.name}' (${user.id}) is valid for 20 years."))
          _    <- Sync[F].delay(println(s"⚠️  Place this value in your service configuration and do not replicate it elsewhere."))
          _    <- Sync[F].delay(println(s"⚠️  If it is lost you may re-run this command with the same service name."))
          _    <- Sync[F].delay(println())
          jwt  <- config.ssoJwtWriter.newJwt(user, Some((365 * 20).days)) // 20 years should be enough
          _    <- Sync[F].delay(println(s"${Console.GREEN}$jwt${Console.RESET}"))
          _    <- Sync[F].delay(println())
        } yield ExitCode.Success
      }
    }

}

