package gpp.sso.service

import cats.effect._
import cats.implicits._
import gpp.sso.model.User
import gpp.sso.service.simulator.SsoSimulator
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.headers.Location
import weaver._

object NewUserSuite extends SimpleIOSuite with Fixture {

  simpleTest("Bob logs in via ORCID as a new GPP user.") {
    SsoSimulator[IO].use { case (sim, sso, reader) =>
      val stage1  = (SsoRoot / "auth" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // stage1 auth should redirect
        res <- sso.get(stage1)(_.pure[IO])
        _   <- expect(res.status == Status.SeeOther).failFast
        loc  = res.headers.get(Location).map(_.uri)
        _   <- expect(loc.isDefined).failFast

        // simulate the user authenticating as Bob, who is a new user
        stage2 <- sim.authenticate(loc.get, Bob, None)

        // stage2 auth should yield a redirect with an auth cookie, with Bob in it
        _   <- sso.get(stage2) { res =>
          for {

            // cookie should exist and JWT body should be a StandardUser for Bob, as a PI
            cu <- reader.findUser(res).flatMap(_.toRight(new RuntimeException("No user.")).liftTo[IO])
            _  <- expectLoggedInAsPi(Bob, cu)

            // the response body should contain the exact same user!
            bu <- res.as[User]
            _  <- expect(cu == bu).failFast

            // the redirect target should be the Explore root
            loc  = res.headers.get(Location).map(_.uri)
            _   <- expect(loc == Some(ExploreRoot)).failFast

          } yield ()
        }
      } yield success
    }
  }

}