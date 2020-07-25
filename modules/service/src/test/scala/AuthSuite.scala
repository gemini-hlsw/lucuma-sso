package gpp.sso.service

import org.http4s.implicits._
import org.http4s._
import gpp.sso.service.simulator.SsoSimulator
import cats.effect._
import cats.implicits._
import weaver._
import org.http4s.headers.Location
import gpp.sso.service.simulator.OrcidSimulator
import org.http4s.client.Client

object AuthSuite extends SimpleIOSuite {

  val SsoRoot     = uri"https://sso.gpp.gemini.edu"
  val ExploreRoot = uri"https://explore.gpp.gemini.edu"

  val sso: Client[IO] = SsoSimulator.client

  simpleTest("Authentication with no cookie.") {
    for {
      // stage1 auth should redirect
      res <- sso.get((SsoRoot / "auth" / "stage1").withQueryParam("state", ExploreRoot))(_.pure[IO])
      _   <- expect(res.status == Status.MovedPermanently).failFast
      loc  = res.headers.get(Location).map(_.uri)
      _   <- expect(loc.isDefined).failFast
      // simulate the user authenticating and returning
      uri <- OrcidSimulator.simulateOrcidAuthentication(loc.get) // TODO: OrcidSimulator.Person.Bob
      _   <- IO(println(uri))
      // stage2 auth should yield a redirect with an auth cookie that we should be able to decode, with Bob in it
      res <- sso.get(uri)(_.pure[IO])
      _   <- IO(println(res))
    } yield success
  }

}