package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.headers.Location

object ApiKeySuite extends SsoSuite with Fixture {

  simpleTest("Create and redeem an API key.") {
    SsoSimulator[IO].use { case (db, sim, sso, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)
        user   <- db.use(_.getStandardUserFromToken(tok))

        // Create an API Key
        apiKey <- db.use(_.createApiKey(user.role.id))

        // Redeem it
        user2  <- db.use(_.getStandardUserFromApiKey(apiKey))

      } yield expect(user == user2)
    } .onError(e => IO(println(e)))
  }

  simpleTest("Delete an API key and try to re-use it.") {
    SsoSimulator[IO].use { case (db, sim, sso, _) =>
      val stage1  = (SsoRoot / "auth" / "v1" / "stage1").withQueryParam("state", ExploreRoot)
      for {

        // Log in as Bob
        redir  <- sso.get(stage1)(_.headers.get(Location).map(_.uri).get.pure[IO])
        stage2 <- sim.authenticate(redir, Bob, None)
        tok    <- sso.get(stage2)(CookieReader[IO].getSessionToken)
        user   <- db.use(_.getStandardUserFromToken(tok))

        // Create an API Key
        apiKey <- db.use(_.createApiKey(user.role.id))

        // Delete it
        _      <- db.use(_.deleteApiKey(apiKey.id))

        // Try to redeem it, should fail
        ex     <- db.use(_.getStandardUserFromApiKey(apiKey)).attempt

      } yield expect(ex.isLeft)
    } .onError(e => IO(println(e)))
  }

}