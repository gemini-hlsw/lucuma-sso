// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect.*
import cats.implicits.*
import io.circe.Json
import io.circe.literal.*
import io.circe.syntax.*
import lucuma.core.model.*
import lucuma.core.util.Gid
import lucuma.sso.client.ApiKey
import lucuma.sso.service.database.RoleRequest.Staff
import lucuma.sso.service.orcid.OrcidIdGenerator
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.Credentials
import org.http4s.Headers
import org.http4s.Method
import org.http4s.QueryParamEncoder
import org.http4s.Request
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.headers.Location
import org.typelevel.ci.CIString

object GraphQLSuite extends SsoSuite with Fixture with FlakyTests with OrcidIdGenerator[IO] {

  implicit def gidQueryParamEncoder[A: Gid]: QueryParamEncoder[A] =
    QueryParamEncoder[String].contramap(Gid[A].fromString.reverseGet)

  def doQueryAs(user: StandardUser, query: String, sso: Client[IO]): IO[Json] =
    for
      jwt <- sso.expect[String](Request[IO](method = Method.POST, uri = SsoRoot / "api" / "v1" / "refresh-token"))

      // Create an API Key
      apiKey <- sso.expect[ApiKey](
        Request[IO](
          method  = Method.POST,
          uri     = (SsoRoot / "api" / "v1" / "create-api-key").withQueryParam("role", user.role.id),
          headers = Headers(Authorization(Credentials.Token(CIString("Bearer"), jwt))),
        )
      )

      // Run a query!
      result <- sso.fetchAs[Json](
        Request[IO](
          method  = Method.POST,
          uri     = SsoRoot / "graphql",
          headers = Headers(Authorization(Credentials.Token(CIString("Bearer"), ApiKey.fromString.reverseGet(apiKey)))),
        ).withEntity(
          Json.obj(
            "query" -> Json.fromString(query)
          )
        )
      )
    yield result

  def queryAsBob(query: String): IO[Json] =
    queryAsBob(_ => query)

  def queryAsBob(query: StandardUser => String): IO[Json] =
    SsoSimulator[IO].use: (db, sim, sso, _, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for
        redir  <- sso.get(stage1)(_.headers.get[Location].map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None) // Log in as Bob
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken) // ensure we have our cookie
        user   <- db.use(_.getStandardUserFromToken(tok))
        result <- doQueryAs(user, query(user), sso)
      yield result
    .onError(e => IO(println(e)))

  def setRole(rid: StandardRole.Id)  =
    (SsoRoot / "auth" / "v1" / "set-role").withQueryParam("role", rid.toString)

  def queryAsStaffBob(query: StandardUser => String): IO[Json] =
    SsoSimulator[IO].use: (db, sim, sso, _, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for
        redir  <- sso.get(stage1)(_.headers.get[Location].map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        tok1   <- sso.get(stage2)(CookieReader[IO].getSessionToken) // ensure we have our cookie
        user1  <- db.use(_.getStandardUserFromToken(tok1))
        rid    <- db.use(_.canonicalizeRole(user1, Staff))
        tok2   <- sso.get(setRole(rid))(CookieReader[IO].getSessionToken)
        user2  <- db.use(_.getStandardUserFromToken(tok2))
        result <- doQueryAs(user2, query(user2), sso)
      yield result
    .onError(e => IO(println(e)))

  def expectQueryAsBob(query: String, expected: Json) =
    queryAsBob(query).map { result =>
      if (result != expected)
          println(s"Result: $result\n\nExpected: $expected")
      expect(result == expected)
    }

  test("Query current role.") {
    flaky()(
      expectQueryAsBob(
        query = """
          query {
            role {
              type
              partner
              user {
                primaryProfile {
                  givenName
                  familyName
                  creditName
                  email
                }
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
                "primaryProfile": {
                  "givenName" : "Bob",
                  "familyName" : "Dobbs",
                  "creditName" : null,
                  "email" : "bob@dobbs.com"
                }
              }
            }
          }
        }"""
      )
    )
  }

  test("Attempt to create API key with invalid role id") {
    flaky()(
      expectQueryAsBob(
        query = """
          mutation {
            createApiKey(role: "bogus")
          }
        """,
        expected = json"""{
          "errors" : [
            {
              "message" : "Not a valid role id: bogus"
            }
          ]
        }"""
      )
    )
  }

  test("Attempt to create API key with a role we don't own") {
    flaky()(
      expectQueryAsBob(
        query = """
          mutation {
            createApiKey(role: "r-99999")
          }
        """,
        expected = json"""{
          "errors" : [
            {
              "message" : "No such role: r-99999"
            }
          ],
          "data" : null
        }"""
      )
    )
  }

  test("Create an API key") {
    flaky()(
      queryAsBob { user =>
        show"""
          mutation {
            createApiKey(role: "${user.role.id}")
          }
        """
      } .map { json =>
        expect(
          json.hcursor
          .downField("data")
          .downField("createApiKey")
          .as[String]
          .toOption
          .map(ApiKey.fromString.getOption)
          .isDefined
        )
      }
    )
  }

  test("Cannot create a pre-auth user as PI"):
    flaky()(
      for
        orcid <- randomOrcidId
        ex    <- expectQueryAsBob(
          show"""
            mutation {
              canonicalizePreAuthUser(
                orcidId: "${orcid.value}",
                fallbackProfile: { email: "biff@henderson.org" }
              ) {
                id
              }
            }
          """,
          expected = json"""{
            "errors" : [
              {
                "message" : "Staff access required for this operation."
              }
            ],
            "data" : null
          }"""
        )
      yield ex
    )

  test("Create a pre-auth user as Staff"):
    flaky()(
      for
        orcid <- randomOrcidId
        json  <- queryAsStaffBob: user =>
          show"""
            mutation {
              canonicalizePreAuthUser(
                orcidId: "${orcid.value}",
                fallbackProfile: { email: "biff@henderson.org" }
              ) {
                orcidId
                fallbackProfile {
                  email
                }
              }
            }
          """
      yield
        val expected = json"""
          {
            "data": {
              "canonicalizePreAuthUser": {
                "orcidId": ${orcid.value.asJson},
                "fallbackProfile": {
                  "email": "biff@henderson.org"
                }
              }
            }
          }
        """
        expect(json == expected)
    )

  test("Edit fallback profile"):
    flaky()(
      for
        orcid <- randomOrcidId
        _     <- queryAsStaffBob: user =>
          show"""
            mutation {
              canonicalizePreAuthUser(
                orcidId: "${orcid.value}",
                fallbackProfile: { email: "biff@henderson.org" }
              ) {
                orcidId
              }
            }
          """
        json  <- queryAsStaffBob: user =>
          show"""
            mutation {
              updateFallback(
                orcidId: "${orcid.value}",
                fallbackProfile: { email: "gavrilo@princip.com" }
              ) {
                orcidId
                fallbackProfile {
                  email
                }
              }
            }
          """

      yield
        val expected = json"""
          {
            "data": {
              "updateFallback": {
                "orcidId": ${orcid.value.asJson},
                "fallbackProfile": {
                  "email": "gavrilo@princip.com"
                }
              }
            }
          }
        """
        expect(json == expected)
    )

  test("Edit unknown orcid profile"):
    flaky()(
      for
        orcid <- randomOrcidId
        json  <- queryAsStaffBob: user =>
          show"""
            mutation {
              updateFallback(
                orcidId: "${orcid.value}",
                fallbackProfile: { email: "gavrilo@princip.com" }
              ) {
                orcidId
                fallbackProfile {
                  email
                }
              }
            }
          """

      yield
        val expected = json"""
          {
            "data": {
              "updateFallback": null
            }
          }
        """
        expect(json == expected)
    )
}