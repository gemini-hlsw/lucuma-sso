package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.sso.model.User
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._

object GuestUserSuite extends SsoSuite with Fixture {

  simpleTest("Anonymous user logs in as a guest.") {
    SsoSimulator[IO].use { case (_, sso, reader) =>
      val guest = (SsoRoot / "api" / "v1" / "authAsGuest")
      sso.run(Request(method = Method.POST, uri = guest)).use { res =>
        for {

          // cookie should exist and JWT body should be a StandardUser for Bob, as a PI
          cu <- reader.findUser(res).flatMap(_.toRight(new RuntimeException("No user.")).liftTo[IO])
          _  <- IO(println(s"cookie user is $cu"))

          // the response body should contain the exact same user!
          bu <- res.as[User]
          _  <- expect(cu == bu).failFast

          // the redirect target should be the Explore root
          _   <- expect(res.status == Status.Ok)

        } yield success
      }
    }
  }

}