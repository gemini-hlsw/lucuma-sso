package gpp.sso.service

import cats._
import cats.effect._
import cats.implicits._
import cats.Monad
import gpp.sso.client._
import gpp.sso.model._
import gpp.sso.service.config._
import gpp.sso.service.config.Environment._
import gpp.sso.service.database.Database
import gpp.sso.service.orcid.OrcidService
import io.circe.syntax._
import io.jaegertracing.Configuration.{ ReporterConfiguration, SamplerConfiguration }
import java.io.{ PrintWriter, StringWriter }
import natchez.{ EntryPoint, Trace }
import natchez.http4s.implicits._
import natchez.jaeger.Jaeger
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Location
import org.http4s.implicits._
import org.http4s.server._
import org.http4s.server.middleware.Logger
import scala.concurrent.duration._
import skunk.Session

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    FMain.main[IO]
}

object FMain {

  val MaxConnections = 10
  val JwtLifetime    = 10.minutes


  // This is the main event. Here are the routes we're serving.
  def routes[F[_]: Defer: Bracket[*[_], Throwable]](
    pool:       Resource[F, Database[F]],
    orcid:      OrcidService[F],
    jwtDecoder: GppJwtDecoder[F],
    jwtEncoder: JwtEncoder[F],
    jwtFactory: JwtFactory[F]
  ): HttpRoutes[F] = {
    object FDsl extends Http4sDsl[F]
    import FDsl._

    // TODO: parameterize the host!
    val Stage2Uri = uri"https://sso.gpp.gemini.edu/auth/stage2"

    // Some parameter matchers. The parameter names are NOT arbitrary! They are requied by ORCID.
    object OrcidCode   extends QueryParamDecoderMatcher[String]("code")
    object RedirectUri extends QueryParamDecoderMatcher[Uri]("state")

    HttpRoutes.of[F] {

      // Create and return a new user
      case POST -> Root / "api" / "v1" / "authAsGuest" =>
        pool.use { db =>
          for {
            gu  <- db.createGuestUser
            clm <- jwtFactory.newClaimForUser(gu)
            jwt <- jwtEncoder.encode(clm)
            r   <- Created((gu:User).asJson.spaces2)
          } yield r.addCookie(Keys.JwtCookie, jwt)
        }

      // Athentication Stage 1. If the user is logged in as a non-guest we're done, otherwise we
      // redirect to ORCID, and on success the user will be redirected back for stage 2.
      case r@(GET -> Root / "auth" / "stage1" :? RedirectUri(redirectUrl)) =>
        r.cookies
          .find(_.name == Keys.JwtCookie)
          .map(_.content)
          .traverse(jwtDecoder.decode)
          .flatMap {

            // If there is no JWT cookie or it's a guest, redirect to ORCID.
            case None | Some(GuestUser(_)) =>
              orcid
                .authenticationUri(Stage2Uri, Some(redirectUrl.toString))
                .flatMap(uri => MovedPermanently(Location(uri)))

            // If it's a service or standard user, we're done.
            case Some(ServiceUser(_, _) | StandardUser(_, _, _, _)) =>
              MovedPermanently(Location(redirectUrl))

          }

      // https://localhost:8080/auth/stage2?code=7y6UQH&state=http://www.google.com

      case GET -> Root / "auth" / "stage2" :? OrcidCode(code) +& RedirectUri(_) =>
        for {
          access <- orcid.getAccessToken(Stage2Uri, code) // when this fails we get a 400 back so we need to handle that case somehow
          person <- orcid.getPerson(access)
          // if user exists
          //  update profile
          //  logged in as guest?
          //    change ownership of objects
          //    delete user
          //  else
          //    upgrade guest user
          //  endif
          // else
          //  logged in as guest?
          //    upgrade user
          //  else
          //    create user
          // endif
          // set jwt
          // redirect to success
          //
          r <- Ok(s"person is $person")
        } yield r

    }
  }

  def poolResource[F[_]: Concurrent: ContextShift: Trace](config: DatabaseConfig): Resource[F, Resource[F, Session[F]]] =
    Session.pooled(
      host     = config.host,
      port     = config.port,
      user     = config.user,
      database = config.database,
      max      = MaxConnections,
    )

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

  def loggingMiddleware[F[_]: Concurrent: ContextShift](
    env: Environment
  ): HttpRoutes[F] => HttpRoutes[F] =
    Logger.httpRoutes[F](
        logHeaders = true,
        logBody    = env == Local,
        redactHeadersWhen = { h =>
          env match {
            case Local             => false
            case Test | Production => Headers.SensitiveHeaders.contains(h)
          }
        }
      )

  def orcidServiceResource[F[_]: Concurrent: Timer: ContextShift](config: OrcidConfig) =
    EmberClientBuilder.default[F].build.map { client =>
      OrcidService(config.clientId, config.clientSecret, client)
    }

  def routesResource[F[_]: Concurrent: ContextShift: Trace: Timer](config: Config) =
    (poolResource[F](config.database), orcidServiceResource(config.orcid))
      .mapN { (pool, orcid) =>
        routes(
          pool       = pool.map(Database.fromSession(_)),
          orcid      = orcid,
          jwtDecoder = GppJwtDecoder.fromJwtDecoder(JwtDecoder.withPublicKey[F](config.publicKey)),
          jwtEncoder = JwtEncoder.withPrivateKey[F](config.privateKey),
          jwtFactory = JwtFactory.withTimeout(JwtLifetime),
        )
      }
      .map(natchezMiddleware(_))
      .map(loggingMiddleware(config.environment))

  def rmain[F[_]: Concurrent: ContextShift: Timer]: Resource[F, ExitCode] =
    for {
      c  <- Resource.liftF(Config.config.load[F])
      ep <- entryPoint
      rs <- ep.liftR(routesResource(c))
      ap  = app(rs)
      _  <- server(8080, ap)
    } yield ExitCode.Success

  def main[F[_]: Concurrent: ContextShift: Timer]: F[ExitCode] =
    rmain.use(_ => Concurrent[F].never[ExitCode])

}

