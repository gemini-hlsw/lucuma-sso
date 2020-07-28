package gpp.sso.service

import org.http4s.implicits._
import org.http4s._
import gpp.sso.service.simulator.SsoSimulator
import cats.effect._
import cats.implicits._
import weaver._
import org.http4s.headers.Location
import gpp.sso.service.simulator.OrcidSimulator
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

  simpleTest("Authentication with no cookie.") {
    for {
      orc <- OrcidSimulator[IO]
      sso <- SsoSimulator.client[IO](orc)
      // stage1 auth should redirect
      res <- sso.get((SsoRoot / "auth" / "stage1").withQueryParam("state", ExploreRoot))(_.pure[IO])
      _   <- expect(res.status == Status.MovedPermanently).failFast
      loc  = res.headers.get(Location).map(_.uri)
      _   <- expect(loc.isDefined).failFast
      // simulate the user authenticating and returning
      uri <- orc.authenticate(loc.get, Bob, None)
      _   <- IO(println(uri))
      // stage2 auth should yield a redirect with an auth cookie that we should be able to decode, with Bob in it
      res <- sso.get(uri)(_.pure[IO])
      _   <- IO(println(res))
    } yield success
  }

}