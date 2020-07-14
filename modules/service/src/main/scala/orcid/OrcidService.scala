package gpp.sso.service.orcid

import cats.effect.Sync
import cats.implicits._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Accept
import org.http4s.implicits._
import org.http4s.headers.Authorization

trait OrcidService[F[_]] {

  /**
   * Construct a URI for redirecting unauthenticated users to ORCID for authentication. ORCID will
   * redirect the user to `redirect` afterward, passing the authentication code on success or an
   * error message on failure. On success, pass `redirect` and the code to `getAccessToken` to
   * complete authentication.
   */
  def authenticationUri(redirect: Uri): F[Uri]

  /**
   * Given a redirect (the same one that was passed to `authenticationUri`) and a resulting
   * authentication code, request an ORCID access token. The resulting structure contains the
   * user's ORCID iD, name, access token, and other useful things.
   */
  def getAccessToken(redirect: Uri, authenticationCode: String): F[AccessInfo]

  /**
   * Retrieve information from the `person` record associated with the given `AccessInfo`.
   */
  def getPerson(accessInfo: AccessInfo): F[PersonInfo]
}

object OrcidService {

  def apply[F[_]: Sync](
    orcidClientId:     String,
    orcidClientSecret: String,
    httpClient:        Client[F],
  ): OrcidService[F] =
    new OrcidService[F] with Http4sClientDsl[F] {

      def authenticationUri(redirect: Uri): F[Uri] =
        uri"https://orcid.org/oauth/authorize"
          .withQueryParams(Map(
            "client_id"     -> orcidClientId,
            "response_type" -> "code",
            "client_secret" -> orcidClientSecret,
            "scope"         -> "/authenticate",
            "grant_type"    -> "authorization_code",
            "redirect_uri"  -> redirect.toString,
          )).pure[F]

      def getAccessToken(redirect: Uri, authenticationCode: String): F[AccessInfo] =
        httpClient.expect(
          Method.POST(
            UrlForm(
              "client_id"     -> orcidClientId,
              "client_secret" -> orcidClientSecret,
              "grant_type"    -> "authorization_code",
              "redirect_uri"  -> redirect.toString,
              "code"          -> authenticationCode,
            ),
            uri"https://orcid.org/oauth/token",
            Accept(MediaType.application.json)
          )
        )

      def getPerson(accessInfo: AccessInfo): F[PersonInfo] =
        httpClient.expect[PersonInfo](
          Method.GET(
            Uri.unsafeFromString(s"https://pub.orcid.org/v3.0/${accessInfo.orcidId.value}/person"), // safe, heh-heh
            Accept(new MediaType("application", "vnd.orcid+xml", true, false)),
            Authorization(Credentials.Token(AuthScheme.Bearer, accessInfo.accessToken.toString))
          )
        )

    }

}
