package lucuma.sso.service

import cats.effect._
import cats.syntax.all._
import lucuma.sso.client.SsoJwtClaim
import org.http4s._
import lucuma.core.model.GuestRole
import lucuma.sso.service.simulator.SsoSimulator

object GuestUserSuite extends SsoSuite with Fixture {

  simpleTest("Anonymous user logs in as a guest.") {
    SsoSimulator[IO]
      .flatMap { case (pool, _, sso, reader) => pool.map(db => (db, sso, reader)) }
      .use { case (db, sso, reader) =>
        sso.run(
          Request(
            method = Method.POST,
            uri    = SsoRoot / "api" / "v1" / "authAsGuest"
          )
        ).use { res =>
          import reader.entityDecoder // note
          for {
            jwt   <- res.as[SsoJwtClaim]                    // response body is a jwt
            user  <- jwt.getUser.liftTo[IO]                 // get the user
            tok   <- CookieReader[IO].getSessionToken(res)  // get the new session token
            userʹ <- db.getGuestUserFromToken(tok)          // redeem it to get the same user
          } yield
            expect(res.status == Status.Created) &&
            expect(user.role  == GuestRole) &&
            expect(user.id == userʹ.id)
        }
      }
    }

}

