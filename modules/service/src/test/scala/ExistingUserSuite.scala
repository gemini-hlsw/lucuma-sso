package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.core.model._
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.headers.Location

object ExistingUserSuite extends SsoSuite with Fixture {

  simpleTest("Log in as existing user.") {
    SsoSimulator[IO].use { case (db, sim, sso, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob, who is new.
        redir  <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)
        user1  <- db.use(_.getStandardUserFromToken(tok))

        // Log in as Bob, who is now an existing user.
        redir  <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, Option(user1).collect { case StandardUser(_, _, _, p) => p.orcidId })
        tok2   <- sso.get(stage2)(CookieReader[IO].getSessionToken)
        user2  <- db.use(_.getStandardUserFromToken(tok2))

      } yield expect(tok != tok2)    && // different tokens
              expect(user2 == user1)    // for the same user
    }
  }

}