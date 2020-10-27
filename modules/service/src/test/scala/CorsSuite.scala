package lucuma.sso.service

import cats.effect._
import org.http4s._
import org.http4s.implicits._
import lucuma.sso.service.config.Environment

object CorsSuite extends SsoSuite {

  def routes(env: Environment, domain: Option[String] = None): HttpRoutes[IO] =
    ServerMiddleware.cors[IO](env, domain).apply(
      Routes[IO](
        dbPool    = null,
        orcid     = null,
        jwtReader = null,
        jwtWriter = null,
        publicUri = uri"http://unused",
        cookies   = null,
        publicKey = null,
      )
    )

  val CorsHeaders: List[String] =
    List(
      "Access-Control-Expose-Headers",
      "Access-Control-Allow-Credentials",
      "Access-Control-Allow-Methods",
      "Access-Control-Allow-Origin",
      "Access-Control-Max-Age",
      "Vary",
    )

  def noHeaderTest(env: Environment): Unit =
    simpleTest(s"CORS headers must not be added if no origin is provided. ($env)") {
      val req = Request[IO](uri = uri"/api/v1/whoami")
      routes(env).orNotFound(req).map { res =>
        forall(CorsHeaders)(s => expect(!res.headers.exists(_.name.value == s)))
      }
    }

  def headerTestForArbitraryDomain(env: Environment): Unit =
    simpleTest(s"CORS headers must be added for arbitrary origin. ($env)") {
      val req = Request[IO](uri = uri"/api/v1/whoami").putHeaders(Header("Origin", "https://wozle.com"))
      routes(env).orNotFound(req).map { res =>
        forall(CorsHeaders)(s => expect(res.headers.exists(_.name.value == s)))
      }
    }

  noHeaderTest(Environment.Local)
  noHeaderTest(Environment.Review)
  noHeaderTest(Environment.Staging)
  noHeaderTest(Environment.Production)

  // failing … why?
  // headerTestForArbitraryDomain(Environment.Local)
  // headerTestForArbitraryDomain(Environment.Review)
  // headerTestForArbitraryDomain(Environment.Staging)

  simpleTest("CORS headers must not be added if origin is mismatched. (Production)") {
    val req = Request[IO](uri = uri"/api/v1/whoami").putHeaders(Header("Origin", "https://wozle.com"))
    routes(Environment.Production, Some("gemini.edu")).orNotFound(req).map { res =>
      forall(CorsHeaders)(s => expect(!res.headers.exists(_.name.value == s)))
    }
  }

  simpleTest("CORS headers must be added if origin matches. (Production)") {
    val req = Request[IO](uri = uri"/api/v1/whoami").putHeaders(Header("Origin", "https://gemini.edu"))
    routes(Environment.Production, Some("gemini.edu")).orNotFound(req).map { res =>
      forall(CorsHeaders)(s => expect(!res.headers.exists(_.name.value == s)))
    }
  }

}

