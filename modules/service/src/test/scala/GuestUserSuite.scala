package gpp.sso.service

import cats.effect._
import gpp.sso.client.Keys
import gpp.sso.model.User
import gpp.sso.service.simulator.SsoSimulator
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import weaver._

object GuestUserSuite extends SimpleIOSuite with Fixture {

  simpleTest("Anonymous user logs in as a guest.") {
    SsoSimulator[IO].use { case (_, sso, decoder) =>
      val guest = (SsoRoot / "api" / "v1" / "authAsGuest")
      sso.run(Request(method = Method.POST, uri = guest)).use { res =>
        for {

          // cookie should exist and JWT body should be a StandardUser for Bob, as a PI
          cu <- decoder.decode(res.cookies.find(_.name == Keys.JwtCookie).get.content)
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