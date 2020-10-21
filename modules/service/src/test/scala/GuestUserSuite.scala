package lucuma.sso.service

import cats.effect._
import cats.syntax.all._
import lucuma.sso.client.SsoJwtClaim
import org.http4s._
import lucuma.core.model.GuestRole
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.headers.Location
import lucuma.core.model.User

object GuestUserSuite extends SsoSuite with Fixture {

  simpleTest("Guest Login.") {
    SsoSimulator[IO]
      .flatMap { case (pool, _, sso, jwtReader) => pool.map(db => (db, sso, jwtReader)) }
      .use { case (db, sso, jwtReader) =>
        sso.run(
          Request(
            method = Method.POST,
            uri    = SsoRoot / "api" / "v1" / "auth-as-guest"
          )
        ).use { res =>
          import jwtReader.entityDecoder // note
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

  simpleTest("Guest user promotion.") {
    val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
    SsoSimulator[IO]
      .flatMap { case (pool, sim, sso, jwtReader) => pool.map(db => (db, sim, sso, jwtReader)) }
      .use { case (db, sim, sso, jwtReader) =>

        val loginAsGuest: IO[(User.Id, SessionToken)] =
          sso.run(
            Request(
              method = Method.POST,
              uri    = SsoRoot / "api" / "v1" / "auth-as-guest"
            )
          ).use { res =>
            import jwtReader.entityDecoder // note
            for {
              jwt   <- res.as[SsoJwtClaim]
              user  <- jwt.getUser.liftTo[IO]
              tok   <- CookieReader[IO].getSessionToken(res)
            } yield (user.id, tok)
          }

        val loginAsBob: IO[(User.Id, SessionToken)] =
          for {
            redir  <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])
            stage2 <- sim.authenticate(redir, Bob, None)
            tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)
            user   <- db.getUserFromToken(tok)
          } yield (user.id, tok)

        // Ok here we go.
        loginAsGuest.flatMap { case (guestId, guestToken) =>
          loginAsBob.flatMap { case (bobId, bobToken) =>
            db.findUserFromToken(guestToken).map { op =>
              expect(op.isEmpty)             && // old token should no longer work
              expect(guestId == bobId)       && // bob and guest have the same id
              expect(guestToken != bobToken)    // and different tokens
            }
          }
        }

      }

  }

}




