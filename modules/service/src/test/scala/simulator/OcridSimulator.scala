package gpp.sso.service.simulator

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server._
import org.http4s.client.Client
import cats.effect.concurrent.Ref
import gpp.sso.service.orcid.OrcidService
import java.util.UUID
import gpp.sso.service.orcid.OrcidAccess
import gpp.sso.model.Orcid
import java.time.Duration
import gpp.sso.service.orcid.OrcidPerson

/** An ORCID simulator with a database of fake users. */
object OrcidSimulator {

  // when an auth request comes in we make a new redirect+code pair
  private val authCodes: Ref[IO, Set[(Uri, String)]] =
    Ref.unsafe(Set.empty)

  // when the code is exchanged we remove it from authCodes and create a new token
  val authTokens: Ref[IO, Set[UUID]] =
    Ref.unsafe(Set.empty)

  // we also return an ORCID iD so we need to create a person to go along with it!
  val people: Ref[IO, Map[Orcid, OrcidPerson]] =
    Ref.unsafe(Map.empty)

  private def genAccess: IO[OrcidAccess] =
    IO {
      OrcidAccess(
        accessToken   = UUID.randomUUID,
        tokenType     = "",
        refreshToken  = UUID.randomUUID,
        expiresIn     = Duration.ofDays(100),
        scope         = "",
        name          = "Rob Norris",
        orcidId       = Orcid.fromString("0000-0003-1301-6629").get,
      )
    }

  private val httpRoutes: HttpRoutes[IO] = {
    object State       extends OptionalQueryParamDecoderMatcher[String]("state")
    object RedirectUri extends QueryParamDecoderMatcher[Uri]("redirect_uri")
    HttpRoutes.of[IO] {

      // This route is bogus, but it simulates the user navigating to ORCID and authenticating,
      // then being redirected back to GPP. We could hook this back into the mock service and
      // close the loop but let's keep it explicit for now.
      case GET -> Root / "oauth" / "authorize" :? RedirectUri(r) +& State(s) =>
        IO(UUID.randomUUID.toString).flatMap { code =>
          val redir = s.foldLeft(r.withQueryParam("code", code))((uri, s) => uri.withQueryParam("state", s))
          authCodes.update(_ + (r -> code)) *>
          Ok(redir.toString)
        }

      // This route turns an auth code (associated with a user) into an access token.
      case r@(POST -> Root / "oauth" / "token") =>
        for {
          data  <- r.as[UrlForm]
          redir  = data.getFirst("redirect_uri").flatMap(s => Uri.fromString(s).toOption)
          code   = data.getFirst("code")
          ok    <- (redir, code).tupled.traverse(p => authCodes.modify(set => (set - p, set(p)))).map(_.getOrElse(false))
          r     <- if (!ok) Forbidden() else genAccess.flatMap(Ok(_))
        } yield r

      case r =>
        IO(println(s"--> OrcidSimulator can't handle $r")) *> NotFound()

    }
  }

  private val client: Client[IO] =
    Client.fromHttpApp(Router("/" -> httpRoutes).orNotFound)

  /**
   * `OrcidService.authenticationUri` -> Uri where the user will call us back. This is *not* testing
   * that the Uri is valid from ORCID's point of view. We assums it is, and only care about the
   * `redirect_uri` and `state` parameters.
   */
  def simulateOrcidAuthentication(authenticationUri: Uri): IO[Uri] =
    for {
      s <- client.expect[String](authenticationUri)
      u <- Uri.fromString(s).liftTo[IO]
    } yield u

  val service: OrcidService[IO] =
    OrcidService(
      httpClient        = client,
      orcidClientId     = "unused",
      orcidClientSecret = "unused"
    )

}

