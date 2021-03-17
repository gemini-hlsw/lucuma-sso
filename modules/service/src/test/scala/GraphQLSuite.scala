// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.headers.Location
import org.http4s.Request
import org.http4s.Method
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.http4s.Credentials
import org.http4s.Headers
import lucuma.core.util.Gid
import org.http4s.QueryParamEncoder
import eu.timepit.refined.auto._
import lucuma.sso.client.ApiKey
import io.circe.Json
import io.circe.literal._
import org.http4s.circe._

object GraphQLSuite extends SsoSuite with Fixture {

  implicit def gidQueryParamEncoder[A: Gid]: QueryParamEncoder[A] =
    QueryParamEncoder[String].contramap(Gid[A].fromString.reverseGet)

  def testQueryAsBob(query: String, expected: Json) =
    SsoSimulator[IO].use { case (_, sim, sso, reader, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        _      <- sso.get(stage2)(CookieReader[IO].getSessionToken) // ensure we have our cookie
        jwt    <- sso.expect[String](Request[IO](method = Method.POST, uri = SsoRoot / "api" / "v1" / "refresh-token"))
        user   <- reader.decodeStandardUser(jwt)

        // Create an API Key
        apiKey <- sso.expect[ApiKey](
          Request[IO](
            method  = Method.POST,
            uri     = (SsoRoot / "api" / "v1" / "create-api-key").withQueryParam("role", user.role.id),
            headers = Headers.of(Authorization(Credentials.Token(CaseInsensitiveString("Bearer"), jwt))),
          )
        )

        // Run a query!
        result <- sso.expect[Json](
          Request[IO](
            method  = Method.POST,
            uri     = SsoRoot / "graphql",
            headers = Headers.of(Authorization(Credentials.Token(CaseInsensitiveString("Bearer"), ApiKey.fromString.reverseGet(apiKey)))),
          ).withEntity(
            Json.obj(
              "query" -> Json.fromString(query)
            )
          )
        )

      } yield expect(result == expected)
    } .onError(e => IO(println(e)))


  simpleTest("Query current role.") {
    testQueryAsBob(
      query = """
        query {
          role {
            type
            partner
            user {
              givenName
              familyName
              creditName
              email
            }
          }
        }
      """,
      expected = json"""{
        "data" : {
          "role" : {
            "type" : "PI",
            "partner" : null,
            "user" : {
              "givenName" : "Bob",
              "familyName" : "Dobbs",
              "creditName" : null,
              "email" : "bob@dobbs.com"
            }
          }
        }
      }"""
    )
  }

}