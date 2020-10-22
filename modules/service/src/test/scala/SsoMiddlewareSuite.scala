package lucuma.sso.service

import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._
import cats.effect.IO
import lucuma.core.model.User
import lucuma.sso.service.config.Config
import eu.timepit.refined.auto._
import lucuma.core.model.GuestUser
import lucuma.sso.client.SsoMiddleware
import org.http4s.client.Client

object SsoMiddlewareSuite extends SsoSuite with Fixture {

  val authedRoots: AuthedRoutes[User, IO] =
    AuthedRoutes.of[User, IO] {
      case GET -> Root / "foo" as u =>
        Ok(s"Got a user: $u")
    }

  val config = Config.local(null)

  simpleTest("Service with SsoMiddleware should accept request with JWT header.") {
    val middleware = SsoMiddleware[IO](config.ssoJwtReader)
    val routes     = middleware(authedRoots)
    val client     = Client.fromHttpApp(routes.orNotFound)
    val user       = GuestUser(User.Id(123L))
    val req        = Request[IO](uri = uri"http://ignored.com/foo")
    for {
      reqʹ <- config.ssoJwtWriter.addAuthorizationHeader(user, req)
      st   <- client.status(reqʹ)
    } yield expect(st == Ok)
  }

  simpleTest("Service with SsoMiddleware should reject request without header.") {
    val middleware = SsoMiddleware[IO](config.ssoJwtReader)
    val routes     = middleware(authedRoots)
    val client     = Client.fromHttpApp(routes.orNotFound)
    val req        = Request[IO](uri = uri"http://ignored.com/foo")
    for {
      st  <- client.status(req)
    } yield expect(st == Forbidden)
  }

}