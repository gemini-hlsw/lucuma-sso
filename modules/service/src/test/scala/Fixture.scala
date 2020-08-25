package lucuma.sso.service

import cats.implicits._
import org.http4s.implicits._
import lucuma.sso.service.orcid._
import lucuma.sso.model._
import lucuma.sso.model.StandardRole._
import weaver.SimpleMutableIOSuite
import cats.effect.IO

trait Fixture { self: SimpleMutableIOSuite =>

  val SsoRoot     = uri"https://sso.lucuma.gemini.edu"
  val ExploreRoot = uri"https://explore.lucuma.gemini.edu"

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
        ),
        OrcidEmail(
          email    = "chunkmonkey69@aol.com",
          verified = false,
          primary  = false,
        )
      )
    )

  def expectLoggedInAsPi(p: OrcidPerson, u: User): IO[Unit] =
    u match {
      case StandardUser(_, Pi(_), Nil, OrcidProfile(_, Some(first), Some(last), None, email)) =>
        for {
          _ <- expect(Option(last)  === p.name.familyName).failFast
          _ <- expect(Option(first) === p.name.givenName).failFast
          _ <- expect(p.emails.find(_.primary).exists(_.email == email)).failFast
        } yield ()
      case _ => expect(false).failFast
    }

}