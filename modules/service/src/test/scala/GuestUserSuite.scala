package lucuma.sso.service

import cats.effect._
import cats.syntax.all._
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s._
import lucuma.core.model.GuestRole
import java.util.UUID

object GuestUserSuite extends SsoSuite with Fixture {

  simpleTest("Anonymous user logs in as a guest.") {
    SsoSimulator[IO].use { case (_, sso, reader) =>
      sso.run(
        Request(
          method = Method.POST,
          uri    = SsoRoot / "api" / "v1" / "authAsGuest"
        )
      ).use { res =>
        for {
          jwt  <- reader.findClaim(res) // response body should be a JWT
          user <- reader.getUser(jwt)   // containing the user
          _    <- res.cookies           // and there should be a cookie containing a UUID
                    .find(_.name == CookieService.CookieName)
                    .map(c => UUID.fromString(c.content))
                    .toRight(new RuntimeException("No cookie!"))
                    .liftTo[IO]
                    // TODO: ... which we can use to fetch another JWT containing the same user
        } yield
          expect(res.status == Status.Created) &&
          expect(user.role  == GuestRole)
      }
    }
  }

}