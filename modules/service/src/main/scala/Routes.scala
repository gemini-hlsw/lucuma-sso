// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.service

import cats.effect._
import cats.implicits._
import gpp.sso.client._
import gpp.sso.model._
import gpp.sso.service.database.Database
import gpp.sso.service.database.RoleRequest
import gpp.sso.service.orcid.OrcidService
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.scalaxml._
import org.http4s.headers.`Content-Type`
import java.security.PublicKey
import gpp.sso.client.util.GpgPublicKeyReader

object Routes {

  // This is the main event. Here are the routes we're serving.
  def apply[F[_]: Sync: Timer](
    dbPool:       Resource[F, Database[F]],
    orcid:        OrcidService[F],
    publicKey:    PublicKey,
    cookieReader: SsoCookieReader[F],
    cookieWriter: SsoCookieWriter[F],
    scheme:       Uri.Scheme,
    authority:    Uri.Authority,
  ): HttpRoutes[F] = {
    object FDsl extends Http4sDsl[F]
    import FDsl._

    // The auth stage 2 URL is sent to ORCID, which redirects the user back. So we need to construct
    // a URL that makes sense to the user's browser!
    val Stage2Uri: Uri =
      Uri(
        scheme    = Some(scheme),     // http[s]
        authority = Some(authority),  // host[:port]
        path      = "/auth/stage2"
      )

    // Some parameter matchers. The parameter names are NOT arbitrary! They are requied by ORCID.
    object OrcidCode   extends QueryParamDecoderMatcher[String]("code")
    object RedirectUri extends QueryParamDecoderMatcher[Uri]("state")

    HttpRoutes.of[F] {

      case r@(GET -> Root) =>
        for {
          u <- cookieReader.attemptFindUser(r)
          r <- Ok(HomePage(u), `Content-Type`(MediaType.text.html))
        } yield r

      // Create and return a new guest user
      // TODO: should we no-op if the user is already logged in (as anyone)?
      case r@(POST -> Root / "api" / "v1" / "authAsGuest") =>
        dbPool.use { db =>
          for {
            gu  <- db.createGuestUser
            c   <- cookieWriter.newCookie(gu, r.isSecure.getOrElse(false))
            r   <- Created((gu:User).asJson.spaces2)
          } yield r.addCookie(c)
        }

      // Show the current user, if any
      case r@(GET -> Root / "api" / "v1" / "whoami") =>
        cookieReader.findUser(r) flatMap {
          case Some(u) => Ok(u.asJson.spaces2)
          case None    => Forbidden("Not logged in.")
        }

      case GET -> Root / "api" / "v1" / "publicKey" =>
        // TODO: only do this once
        for {
          text <- GpgPublicKeyReader.armorText(publicKey).liftTo[F]
          r    <- Ok(text)
        } yield r

      // Log out. TODO: If it's a guest user, delete that user.
      case POST -> Root / "api" / "v1" / "logout" =>
        cookieWriter.removeCookie.flatMap(c => Ok("Logged out").map(_.removeCookie(c)))

      // Athentication Stage 1. If the user is logged in as a non-guest we're done, otherwise we
      // redirect to ORCID, and on success the user will be redirected back for stage 2.
      case r@(GET -> Root / "auth" / "stage1" :? RedirectUri(redirectUrl)) =>
        cookieReader.findUser(r).flatMap {

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
            oguestId <- cookieReader.findUser(r).map(_.collect { case GuestUser(id) => id})
            pair     <- db.upsertOrPromoteUser(access, person, oguestId, RoleRequest.Pi)
            (user, chown) = pair
            _        <- oguestId.traverse { guestId =>
                          Sync[F].delay(println(s"TODO: chown $guestId -> ${user.id}")) *> // TODO!
                          db.deleteUser(guestId)
                        } .whenA(chown)
            cookie   <- cookieWriter.newCookie(user, r.isSecure.getOrElse(false))
            res      <- Found(Location(redir), (user:User).asJson.spaces2)
          } yield res.addCookie(cookie)
        }

    }
  }

}


