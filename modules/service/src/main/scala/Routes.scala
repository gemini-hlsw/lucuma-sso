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
import org.http4s.implicits._

object Routes {

  // This is the main event. Here are the routes we're serving.
  def apply[F[_]: Sync: Timer](
    dbPool:       Resource[F, Database[F]],
    orcid:        OrcidService[F],
    cookieReader: SsoCookieReader[F],
    cookieWriter: SsoCookieWriter[F],
  ): HttpRoutes[F] = {
    object FDsl extends Http4sDsl[F]
    import FDsl._

    // TODO: parameterize the host!
    val Stage2Uri = uri"https://sso.gpp.gemini.edu/auth/stage2"

    // Some parameter matchers. The parameter names are NOT arbitrary! They are requied by ORCID.
    object OrcidCode   extends QueryParamDecoderMatcher[String]("code")
    object RedirectUri extends QueryParamDecoderMatcher[Uri]("state")

    HttpRoutes.of[F] {

      // Create and return a new guest user
      // TODO: should we no-op if the user is already logged in (as anyone)?
      case POST -> Root / "api" / "v1" / "authAsGuest" =>
        dbPool.use { db =>
          for {
            gu  <- db.createGuestUser
            c   <- cookieWriter.newCookie(gu)
            r   <- Created((gu:User).asJson.spaces2)
          } yield r.addCookie(c)
        }

      // Athentication Stage 1. If the user is logged in as a non-guest we're done, otherwise we
      // redirect to ORCID, and on success the user will be redirected back for stage 2.
      case r@(GET -> Root / "auth" / "stage1" :? RedirectUri(redirectUrl)) =>
        cookieReader.findUser(r).flatMap {

            // If there is no JWT cookie or it's a guest, redirect to ORCID.
            case None | Some(GuestUser(_)) =>
              orcid
                .authenticationUri(Stage2Uri, Some(redirectUrl.toString))
                .flatMap(uri => SeeOther(Location(uri)))

            // If it's a service or standard user, we're done.
            case Some(ServiceUser(_, _) | StandardUser(_, _, _, _)) =>
              SeeOther(Location(redirectUrl))

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
            cookie   <- cookieWriter.newCookie(user)
            res      <- SeeOther(Location(redir), (user:User).asJson.spaces2)
          } yield res.addCookie(cookie)
        }

    }
  }

}

