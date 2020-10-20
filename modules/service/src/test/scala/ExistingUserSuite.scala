package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.core.model._
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.headers.Location

object ExistingUserSuite extends SsoSuite with Fixture {

  simpleTest("Bob logs in via ORCID as a new lucuma user, then logs in again.") {
    SsoSimulator[IO].use { case (db, sim, sso, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Stage 1
        redir <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])

        // Log into ORCID as Bob, who is new.
        stage2 <- sim.authenticate(redir, Bob, None)

        // Stage 2 will create the user in lucuma and set the session token it
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)

        // need to find the standard user now

        user1  <- db.use(_.getStandardUserFromToken(tok))

        // Log into ORCID as Bob, who is now an existing user.
        stage2 <- sim.authenticate(redir, Bob, Option(user1).collect { case StandardUser(_, _, _, p) => p.orcidId })

        // Stage 2 should fetch the existing user this time.
        tok2   <- sso.get(stage2)(CookieReader[IO].getSessionToken)

        // Tokens should be differemny
        _      <- expect(tok != tok2).failFast

        user2  <- db.use(_.getStandardUserFromToken(tok2))

        // Should be the same user!
        _      <- expect(user2 == user1).failFast

      } yield success
    }
  }

}