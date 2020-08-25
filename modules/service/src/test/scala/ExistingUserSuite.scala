package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.sso.model._
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.headers.Location
import org.http4s.Request

object ExistingUserSuite extends SsoSuite with Fixture {

  simpleTest("Bob logs in via ORCID as a new lucuma user, then logs in again.") {
    SsoSimulator[IO].use { case (sim, sso, _) =>
      val stage1  = (SsoRoot / "auth" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Stage 1
        redir <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])

        // Log into ORCID as Bob, who is new.
        stage2 <- sim.authenticate(redir, Bob, None)

        // Stage 2 will create the user in lucuma and return it
        user1  <- sso.fetchAs[User](Request[IO](uri =stage2))

        // Log into ORCID as Bob, who is now an existing user.
        stage2 <- sim.authenticate(redir, Bob, Option(user1).collect { case StandardUser(_, _, _, p) => p.orcid })

        // Stage 2 should fetch the existing user this time.
        user2  <- sso.fetchAs[User](Request[IO](uri =stage2))

        // Should be the same user!
        _      <- expect(user2 == user1).failFast

      } yield success
    }
  }

}