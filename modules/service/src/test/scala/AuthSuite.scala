package gpp.sso.service

import org.http4s.implicits._
import org.http4s._
import gpp.sso.service.simulator.SsoSimulator
import cats.effect._
import cats.implicits._
import weaver._
import org.http4s.headers.Location
import gpp.sso.service.orcid.OrcidPerson
import gpp.sso.service.orcid.OrcidName
import gpp.sso.service.orcid.OrcidEmail

object AuthSuite extends SimpleIOSuite {

  val SsoRoot     = uri"https://sso.gpp.gemini.edu"
  val ExploreRoot = uri"https://explore.gpp.gemini.edu"

  val Bob: OrcidPerson =
    OrcidPerson(
      name = OrcidName(
        familyName = Some("Dobbs"),
        givenName  = Some("Bob"),
        creditName = None
      ),
      emails = List(
        OrcidEmail(
          email    = "bob@dobbs.com",
          verified = true,
          primary  = true,
        )
      )
    )

  override def simpleTest(name: String)(run: => IO[Expectations]): Unit =
    super.simpleTest(name)(run.onError { case t => IO(t.printStackTrace) })

  simpleTest("Authentication with no cookie.") {
    SsoSimulator[IO].use { case (sim, sso) =>
      val st1  = (SsoRoot / "auth" / "stage1").withQueryParam("state", ExploreRoot)
      for {
        // stage1 auth should redirect
        res <- sso.get(st1)(_.pure[IO])
        _   <- expect(res.status == Status.MovedPermanently).failFast
        loc  = res.headers.get(Location).map(_.uri)
        _   <- expect(loc.isDefined).failFast
        // simulate the user authenticating as Bob, who is a new user
        st2 <- sim.authenticate(loc.get, Bob, None)
        // stage2 auth should yield a redirect with an auth cookie that we should be able to decode,
        // with Bob in it
        res <- sso.get(st2)(_.as[String])
        _   <- IO(println(res))
      } yield success
    }
  }

}