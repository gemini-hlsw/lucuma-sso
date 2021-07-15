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
import org.typelevel.ci.CIString
import org.http4s.Credentials
import org.http4s.Headers
import lucuma.core.util.Gid
import org.http4s.QueryParamEncoder
import lucuma.core.model.StandardRole
import eu.timepit.refined.auto._
import org.http4s.Status
import lucuma.sso.client.ApiKey
import lucuma.sso.client.SsoJwtClaim
import lucuma.core.model.ServiceUser
import lucuma.core.model.User

object ApiKeySuite extends SsoSuite with Fixture {

  implicit def gidQueryParamEncoder[A: Gid]: QueryParamEncoder[A] =
    QueryParamEncoder[String].contramap(Gid[A].fromString.reverseGet)

  test("Create and redeem an API key.") {
    SsoSimulator[IO].use { case (db, sim, sso, reader, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get[Location].map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        _      <- sso.get(stage2)(CookieReader[IO].getSessionToken) // ensure we have our cookie

        // Exchange cookie for JWT
        jwt    <- sso.expect[String](
          Request[IO](
            method = Method.POST,
            uri = SsoRoot / "api" / "v1" / "refresh-token",
          )
        )

        // Get the user out of the JWT
        user   <- reader.decodeStandardUser(jwt)

        // Create an API Key
        apiKey <- sso.expect[ApiKey](
          Request[IO](
            method  = Method.POST,
            uri     = (SsoRoot / "api" / "v1" / "create-api-key").withQueryParam("role", user.role.id),
            headers = Headers(Authorization(Credentials.Token(CIString("Bearer"), jwt))),
          )
        )

        // Redeem it (TODO: use API but we need a service user to do it)
        user  <- db.use(_.findStandardUserFromApiKey(apiKey))

      } yield expect(user.exists(_.displayName == "Bob Dobbs"))
    } .onError(e => IO(println(e)))
  }

  test("Delete an API key and try to re-use it.") {
    SsoSimulator[IO].use { case (db, sim, sso, _, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get[Location].map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)
        user   <- db.use(_.getStandardUserFromToken(tok))

        // Create an API Key
        apiKey <- db.use(_.createApiKey(user.role.id))

        // Delete it
        _      <- db.use(_.deleteApiKey(apiKey.id))

        // Try to redeem it, should fail
        user2  <- db.use(_.findStandardUserFromApiKey(apiKey))

      } yield expect(user2.isEmpty)
    } .onError(e => IO(println(e)))
  }

  test("Can't create an API key for someone else!") {
    SsoSimulator[IO].use { case (_, sim, sso, _, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get[Location].map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        _      <- sso.get(stage2)(CookieReader[IO].getSessionToken) // ensure we have our cookie

        // Exchange cookie for JWT
        jwt    <- sso.expect[String](
          Request[IO](
            method = Method.POST,
            uri = SsoRoot / "api" / "v1" / "refresh-token",
          )
        )

        // Create an API Key
        status <- sso.status(
          Request[IO](
            method  = Method.POST,
            uri     = (SsoRoot / "api" / "v1" / "create-api-key").withQueryParam("role", StandardRole.Id(1L)), // no real role with id=1
            headers = Headers(Authorization(Credentials.Token(CIString("Bearer"), jwt))),
          )
        )

      } yield expect(status == Status.Forbidden)
    } .onError(e => IO(println(e)))
  }


  test("Promote an API key.") {
    SsoSimulator[IO].use { case (db, sim, sso, reader, writer) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      val Bearer = CIString("Bearer")
      import reader.entityDecoder
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get[Location].map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)
        user   <- db.use(_.getStandardUserFromToken(tok))

        // Create an API Key
        apiKey <- db.use(_.createApiKey(user.role.id))

        // Promote it to a JWT

        serviceJwt <- writer.newJwt(ServiceUser(User.Id(1L), "bogus")) // need to call as a service user

        jwt <- sso.expect[SsoJwtClaim](
                Request[IO](
                  uri     = (SsoRoot / "api" / "v1" / "exchange-api-key").withQueryParam("key", apiKey),
                  headers = Headers(Authorization(Credentials.Token(Bearer, serviceJwt)))
                )
              )

      } yield expect(jwt.getUser == Right(user))
    } .onError(e => IO(println(e)))
  }

  test("Can't promote an API key without a user.") {
    SsoSimulator[IO].use { case (db, sim, sso, _, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get[Location].map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)
        user   <- db.use(_.getStandardUserFromToken(tok))

        // Create an API Key
        apiKey <- db.use(_.createApiKey(user.role.id))

        // Promote it to a JWT
        status <- sso.status(
                    Request[IO](
                      uri = (SsoRoot / "api" / "v1" / "exchange-api-key").withQueryParam("key", apiKey),
                    )
                  )


      } yield expect(status == Status.Forbidden)
    } .onError(e => IO(println(e)))
  }

}