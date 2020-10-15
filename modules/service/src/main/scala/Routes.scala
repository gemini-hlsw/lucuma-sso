// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.sso.client._
import lucuma.core.model._
import lucuma.sso.service.database.Database
import lucuma.sso.service.database.RoleRequest
import lucuma.sso.service.orcid.OrcidService
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.scalaxml._
import org.http4s.headers.`Content-Type`

object Routes {

  // This is the main event. Here are the routes we're serving.
  def apply[F[_]: Sync: Timer](
    dbPool:    Resource[F, Database[F]],
    orcid:     OrcidService[F],
    jwtReader: SsoJwtReader[F],
    jwtWriter: SsoJwtWriter[F],
    cookies:   CookieService[F],
    publicUri: Uri, // root URI
  ): HttpRoutes[F] = {
    object FDsl extends Http4sDsl[F]
    import FDsl._

    // The auth stage 2 URL is sent to ORCID, which redirects the user back. So we need to construct
    // a URL that makes sense to the user's browser!
    val Stage2Uri: Uri =
      publicUri.copy(path = "/auth/stage2")

    // Some parameter matchers. The parameter names are NOT arbitrary! They are requied by ORCID.
    object OrcidCode   extends QueryParamDecoderMatcher[String]("code")
    object RedirectUri extends QueryParamDecoderMatcher[Uri]("state")

    HttpRoutes.of[F] {

      case r@(GET -> Root) =>
        for {
          u <- jwtReader.attemptFindUser(r)
          r <- Ok(HomePage(publicUri, u), `Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        } yield r

      // Create and return a new guest user
      // TODO: should we no-op if the user is already logged in (as anyone)?
      case POST -> Root / "api" / "v1" / "authAsGuest" =>
        dbPool.use { db =>
          for {
            gu  <- db.createGuestUser
            jwt <- jwtWriter.newJwt(gu)
            tok <- TokenService[F](db, jwtWriter).issueToken(gu)
            c   <- cookies.cookie(tok)
            r   <- Created(jwt)
          } yield r.addCookie(c)
        }

      // Log out. TODO: If it's a guest user, delete that user.
      case POST -> Root / "api" / "v1" / "logout" =>
        ??? // TODO

      // Athentication Stage 1. If the user is logged in as a non-guest we're done, otherwise we
      // redirect to ORCID, and on success the user will be redirected back for stage 2.
      case r@(GET -> Root / "auth" / "stage1" :? RedirectUri(redirectUrl)) =>
        jwtReader.findUser(r).flatMap {

            // If there is no JWT cookie or it's a guest, redirect to ORCID.
            case None | Some(GuestUser(_)) =>
              orcid
                .authenticationUri(Stage2Uri, Some(redirectUrl.toString))
                .flatMap(uri => Found(Location(uri)))

            // If it's a service or standard user, we're done.
            case Some(ServiceUser(_, _) | StandardUser(_, _, _, _)) =>
              Found(Location(redirectUrl))

          }

      // Authentication Stage 2.
      case r@(GET -> Root / "auth" / "stage2" :? OrcidCode(code) +& RedirectUri(redir)) =>
        dbPool.use { db =>
          for {
            access   <- orcid.getAccessToken(Stage2Uri, code) // when this fails we get a 400 back so we need to handle that case somehow
            person   <- orcid.getPerson(access)
            oguestId <- jwtReader.findUser(r).map(_.collect { case GuestUser(id) => id})
            pair     <- db.upsertOrPromoteUser(access, person, oguestId, RoleRequest.Pi)
            (user, chown) = pair
            _        <- oguestId.traverse { guestId =>
                          Sync[F].delay(println(s"TODO: chown $guestId -> ${user.id}")) *> // TODO!
                          db.deleteUser(guestId)
                        } .whenA(chown)
            // TODO: add refresh cookie
            // cookie   <- jwtWriter.newCookie(user, publicUri.scheme == Some(Scheme.https))
            res      <- Found(Location(redir))
          } yield res //.addCookie(cookie)
        }

    }
  }

}


