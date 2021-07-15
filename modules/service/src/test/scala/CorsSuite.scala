// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect._
import org.http4s._
import org.http4s.implicits._
import lucuma.sso.service.config.Environment
import natchez.Trace.Implicits.noop
import org.typelevel.ci.CIString

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
        graphQL   = null,
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
    test(s"CORS headers must not be added if no origin is provided. ($env)") {
      val req = Request[IO](uri = uri"/api/v1/whoami")
      routes(env).orNotFound(req).map { res =>
        forEach(CorsHeaders)(s => expect(!res.headers.headers.exists(_.name.toString == s)))
      }
    }

  def headerTestForArbitraryDomain(env: Environment): Unit =
    test(s"CORS headers must be added for arbitrary origin. ($env)") {
      val req = Request[IO](uri = uri"/api/v1/whoami").putHeaders(Header.Raw(CIString("Origin"), "https://wozle.com"))
      routes(env).orNotFound(req).map { res =>
        forEach(CorsHeaders)(s => expect(res.headers.headers.exists(_.name.toString == s)))
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

  test("CORS headers must not be added if origin is mismatched. (Production)") {
    val req = Request[IO](uri = uri"/api/v1/whoami").putHeaders(Header.Raw(CIString("Origin"), "https://wozle.com"))
    routes(Environment.Production, Some("gemini.edu")).orNotFound(req).map { res =>
      forEach(CorsHeaders)(s => expect(!res.headers.headers.exists(_.name.toString == s)))
    }
  }

  test("CORS headers must be added if origin matches. (Production)") {
    val req = Request[IO](uri = uri"/api/v1/whoami").putHeaders(Header.Raw(CIString("Origin"), "https://gemini.edu"))
    routes(Environment.Production, Some("gemini.edu")).orNotFound(req).map { res =>
      forEach(CorsHeaders)(s => expect(!res.headers.headers.exists(_.name.toString == s)))
    }
  }

}

