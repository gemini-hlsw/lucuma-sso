// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect._
import cats.implicits._
import lucuma.sso.service.database.Database
import lucuma.sso.service.database.RoleRequest
import lucuma.sso.service.orcid.OrcidService
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

object Routes {

  // This is the main event. Here are the routes we're serving.
  def apply[F[_]: Sync: Timer](
    dbPool:    Resource[F, Database[F]],
    orcid:     OrcidService[F],
    jwtWriter: SsoJwtWriter[F],
    cookies:   CookieService[F],
    publicUri: Uri, // root URI
  ): HttpRoutes[F] = {
    object FDsl extends Http4sDsl[F]
    import FDsl._

    // The auth stage 2 URL is sent to ORCID, which redirects the user back. So we need to construct
    // a URL that makes sense to the user's browser!
    val Stage2Uri: Uri =
      publicUri.copy(path = "/auth/v1/stage2")

    // Some parameter matchers. The parameter names are NOT arbitrary! They are requied by ORCID.
    object OrcidCode   extends QueryParamDecoderMatcher[String]("code")
    object RedirectUri extends QueryParamDecoderMatcher[Uri]("state")

    HttpRoutes.of[F] {

      case r@(POST -> Root / "api" / "v1" / "refresh-token") =>
        cookies.findSessionToken(r).flatMap {
          case None => Forbidden("Not logged in.")
          case Some(tok) =>
            dbPool.use { db =>
              for {
                user <- db.getStandardUserFromToken(tok)
                jwt  <- jwtWriter.newJwt(user)
                res  <- Ok(jwt)
              } yield res
            }
        }

      // Authenticate as a guest. Response body is the new user's JWT, and a refresh token is set.
      case POST -> Root / "api" / "v1" / "auth-as-guest" =>
        dbPool.use { db =>
          db.createGuestUserAndSessionToken.flatMap { case (user, token) =>
            for {
              jwt <- jwtWriter.newJwt(user)
              res <- Created(jwt)
              coo <- cookies.sessionCookie(token)
            } yield res.addCookie(coo)
          }
        }

      // Authentication Stage 1: Send the user to ORCID.
      case GET -> Root / "auth" / "v1" / "stage1" :? RedirectUri(redirectUrl) =>
        for {
          uri <- orcid.authenticationUri(Stage2Uri, Some(redirectUrl.toString))
          res <- Found(Location(uri))
        } yield res

      // Authentication Stage 2: The user has returned from ORCID.
      // Insert/update a standard user, set a refresh token, and redirect to wherever the user asked to go in Stage 1.
      case r@(GET -> Root / "auth" / "v1" / "stage2" :? OrcidCode(code) +& RedirectUri(redir)) =>
        dbPool.use { db =>
          for {
            access   <- orcid.getAccessToken(Stage2Uri, code) // when this fails we get a 400 back so we need to handle that case somehow
            person   <- orcid.getPerson(access)
            otoken   <- cookies.findSessionToken(r)
            oguest   <- otoken.flatTraverse(db.findGuestUserFromToken)
            token    <-
              oguest match {
                case None =>
                  db.canonicalizeUser(access, person, RoleRequest.Pi)
                case Some(g) =>
                  db.promoteGuestUser(access, person, g.id, RoleRequest.Pi).flatMap {
                    case (None, tok) => tok.pure[F]
                    case (Some(existing), tok) =>
                      Sync[F].delay(println(s"==> TODO: chown ${g.id} -> $existing"))
                        .as(tok)
                  }
              }
            cookie   <- cookies.sessionCookie(token)
            res      <- Found(Location(redir))
          } yield res.addCookie(cookie)
        }

      // Log out. TODO: If it's a guest user, delete that user.
      case POST -> Root / "api" / "v1" / "logout" =>
        ??? // TODO

    }
  }

}


