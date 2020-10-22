package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s._
import org.http4s.headers.Location
import lucuma.core.model.Access

object NewUserSuite extends SsoSuite with Fixture {

  simpleTest("Bob logs in via ORCID as a new lucuma user.") {
    SsoSimulator[IO].use { case (db, sim, sso, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // stage1 auth should redirect
        res <- sso.get(stage1)(_.pure[IO])
        _   <- expect(res.status == Status.Found).failFast
        loc  = res.headers.get(Location).map(_.uri)
        _   <- expect(loc.isDefined).failFast

        // simulate the user authenticating as Bob, who is a new user
        stage2 <- sim.authenticate(loc.get, Bob, None)

        // stage2 auth should yield a redirect with a session cookie
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)

        bob  <- db.use(_.getStandardUserFromToken(tok))

        _ <- expect(bob.role.access == Access.Pi).failFast
        _ <- expect(bob.profile.familyName == Bob.name.familyName).failFast

      } yield success
    }
  }

}