// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.simulator

import cats.data.OptionT
import cats.effect._
import cats.effect.Ref
import cats.implicits._
import lucuma.core.model.OrcidId
import lucuma.sso.service.orcid._
import java.time.Duration
import java.util.UUID
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server._
import scala.util.Random

/** An ORCID simulator. */
trait OrcidSimulator[F[_]] {
  def client:  Client[F]
  def authenticate(uri: Uri, as: OrcidPerson, id: Option[OrcidId]): F[Uri]
}

object OrcidSimulator {

  type Code = String
  type Token = UUID

  def apply[F[_]: Async]: F[OrcidSimulator[F]] =
    for {

      // Our database is a map from ORCID iDs to Person records. When we simulate authentication we add
      // the record before returning the athentication code. There is no sense in which we need to
      // test "failure" because if the user can't log in we'll never hear from them again. We only get
      // a callback on success.
      people <- Ref[F].of(Map.empty[OrcidId, OrcidPerson])

      // After authentication we add an entry to the pending token exchanges.
      pending <- Ref[F].of(Map.empty[(Uri, Code), OrcidId])

      // When we do a token exchange we add an access token to this map.
      tokens  <- Ref[F].of(Map.empty[Token, OrcidId])

    } yield new OrcidSimulator[F] with Http4sDsl[F] {

      object State       extends OptionalQueryParamDecoderMatcher[String]("state")
      object RedirectUri extends QueryParamDecoderMatcher[Uri]("redirect_uri")

      def genAccess(o: OrcidId, p: OrcidPerson): F[OrcidAccess] =
        Sync[F].delay {
          OrcidAccess(
            accessToken   = UUID.randomUUID,
            tokenType     = "",
            refreshToken  = UUID.randomUUID,
            expiresIn     = Duration.ofDays(100),
            scope         = "",
            name          = p.name.displayName(o),
            orcidId       = o,
          )
        }

      def exchangeToken(key: (Uri, Code)): F[OrcidAccess] =
        pending.modify(m => (m - key, m.get(key))).flatMap {
          case Some(orcid) =>
            people.get.map(m => m.get(orcid)) flatMap {
              case Some(p) => genAccess(orcid, p).flatTap { acc =>
                tokens.update(m => m.updated(acc.accessToken, orcid))
              }
              case None => Sync[F].raiseError(new RuntimeException(s"Unknown ORCID iD $orcid"))
            }
          case None => Sync[F].raiseError(new RuntimeException(s"No pending access for $key"))
        }

      val httpRoutes: HttpRoutes[F] = {
        HttpRoutes.of[F] {

          case r@(POST -> Root / "oauth" / "token") =>
            for {
              data  <- r.as[UrlForm]
              redir  = data.getFirst("redirect_uri").flatMap(s => Uri.fromString(s).toOption)
              code   = data.getFirst("code")
              oacc  <- (redir, code).tupled.traverse(exchangeToken)
              r     <- oacc.fold(Forbidden())(Ok(_))
            } yield r

          case GET -> Root / "v3.0" / who / "person" =>
            OrcidId.fromValue(who) match {
              case Right(orcid) =>
                people.get.map(_.get(orcid)).flatMap {
                  case Some(person) => Ok(person)
                  case None         => NotFound()
                }
              case Left(_) => NotFound()
            }

          case r =>
            Sync[F].delay(println(s"--> OrcidSimulator can't handle $r")) *> NotFound()

        }
      }

      def client: Client[F] =
        Client.fromHttpApp(Router("/" -> httpRoutes).orNotFound)

      def randomOrcidId: F[OrcidId] =
        Sync[F].delay {
          def digit = Random.nextInt(10).toString
          val a, b, c = List.fill(4)(digit).mkString
          val d = List.fill(3)(digit).mkString
          val x = checkDigit(a + b + c + d)
          OrcidId.fromValue(s"$a-$b-$c-$d$x") match {
            case Left(s)  => sys.error(s)
            case Right(o) => o
          }
        }

      // Copied from OrcidId.scala, where it is private :-\
      def checkDigit(baseDigits: String): String = {
        require(baseDigits.forall(c => c >= '0' && c <= '9'))
        val total = baseDigits.foldLeft(0) { (acc, c) =>
          val digit = c - '0'
          (acc + digit) * 2
        }
        val remainder = total % 11
        val result    = (12 - remainder) % 11
        if (result == 10) "X" else result.toString
      }

      def authenticate(uri: Uri, person: OrcidPerson, id: Option[OrcidId]): F[Uri] =
        Request[F](uri = uri) match {

          case GET -> Root / "oauth" / "authorize" :? RedirectUri(r) +& State(s) =>
            for {

              // Add the person to our database, using a random ORCID iD if none is given. This
              // simulates a new user.
              orcid <- OptionT.fromOption[F](id).getOrElseF(randomOrcidId)
              _     <- people.update(_ + (orcid -> person))

              // Add an auth code to the pending list.
              code  <- Sync[F].delay(UUID.randomUUID.toString)
              _     <- pending.update(m => m.updated((r, code), orcid))

            } yield r.withQueryParam("code", code).withOptionQueryParam("state", s)

          case req => Async[F].raiseError(new RuntimeException(s"Unexpected request: $req"))

        }

    }

}

