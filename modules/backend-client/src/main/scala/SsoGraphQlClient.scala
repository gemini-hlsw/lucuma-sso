// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client

import cats.effect.Async
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.option.*
import clue.ErrorPolicy
import clue.GraphQLOperation
import clue.http4s.Http4sHttpBackend
import clue.http4s.Http4sHttpClient
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.*
import lucuma.core.model.OrcidId
import lucuma.core.model.OrcidProfile
import lucuma.core.model.StandardRole
import lucuma.core.model.StandardUser
import lucuma.core.model.User
import lucuma.core.model.UserProfile
import lucuma.sso.client.codec.userProfile.given
import org.http4s.Credentials
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger

/**
 * A client that facilitates working with the SSO GraphQL API.
 */
trait SsoGraphQlClient[F[_]]:

  /**
   * Creates a pre-authentication user and records it in the user database.
   *
   * @return new user, or existing user if a user with this ORCiD already
   *         exists
   */
  def canonicalizePreAuthUser(orcidId: OrcidId): F[User]

object SsoGraphQlClient:
  def apply[F[_]](using ev: SsoGraphQlClient[F]): SsoGraphQlClient[F] = ev

  def create[F[_]: Async: Logger](
    uri:        Uri,
    client:     Client[F],
    serviceJwt: String
  ): F[SsoGraphQlClient[F]] =

    val authorization: Authorization =
      Authorization(Credentials.Token(CIString("Bearer"), serviceJwt))

    Http4sHttpClient
      .of[F, Unit](uri, headers = Headers(authorization))(Async[F], Http4sHttpBackend(client), Logger[F])
      .map: http =>
        new SsoGraphQlClient[F]:
          override def canonicalizePreAuthUser(orcidId: OrcidId): F[User] =
            for
              _ <- Logger[F].debug(s"canonicalizePreAuthUser($orcidId)")
              u <- http.request(CanonicalizePreAuthUser, "CanonicalizePreAuthUser".some)(using ErrorPolicy.RaiseAlways)
                       .withInput(orcidId)
              _ <- Logger[F].debug(s"canonicalizePreAuthUser: $u")
            yield u

object CanonicalizePreAuthUser extends GraphQLOperation[Unit]:
  type Variables = OrcidId
  type Data      = User

  override val varEncoder: Encoder.AsObject[Variables] =
    Encoder.AsObject.instance[Variables]: input =>
      JsonObject("orcidId" -> input._1.asJson)

  // The `User` codec in `lucuma.sso.client.codec.user` doesn't correspond
  // to the schema so we'll make a decoder here for the special case of
  // creating a pre-auth standard user.
  given Decoder[User] =
    (c: HCursor) =>
      for
        id <- c.get[User.Id]("id")
        oi <- c.get[OrcidId]("orcidId")
        pr <- c.get[UserProfile]("profile")
        sr <- c.downField("roles").downArray.downField("id").as[StandardRole.Id]
      yield StandardUser(id, StandardRole.Pi(sr), Nil, OrcidProfile(oi, pr))

  override val dataDecoder: Decoder[User] =
    (c: HCursor) => c.get[User]("canonicalizePreAuthUser")

  override val document: String =
    """
      mutation CanonicalizePreAuthUser($orcidId: OrcidId!) {
        canonicalizePreAuthUser(orcidId: $orcidId) {
          id
          orcidId
          profile {
            givenName
            familyName
            creditName
            email
          }
          roles {
            id
            type
          }
        }
      }
    """