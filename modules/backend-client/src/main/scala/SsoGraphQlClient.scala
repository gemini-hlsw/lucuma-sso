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
   * Creates a pre-authentication user and records it in the user database
   * along with its fallback profile information. If a user with the given
   * ORCiD already exists, its fallback profile is updated.
   *
   * @return new user, or existing user if a user with this ORCiD already
   *         exists
   */
  def canonicalizePreAuthUser(
    orcidId:         OrcidId,
    fallbackProfile: UserProfile
  ): F[User]

  /**
   * Updates the fallback profile for a user with the given ORCiD, if any.
   *
   * @return Some updated user, or None if there is no user corresponding to
   *         orcidId
   */
  def updateFallback(
    orcidId:         OrcidId,
    fallbackProfile: UserProfile
  ): F[Option[User]]

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
        new SsoGraphQlClient[F] with SsoGraphQlClientOps:

          private def go(
            op:              PreAuthUserOperation,
            name:            String,
            orcidId:         OrcidId,
            fallbackProfile: UserProfile
          ): F[op.Data] =
            for
              _ <- Logger[F].debug(s"$name($orcidId, ${fallbackProfile.asJson.spaces2})")
              u <- http.request(op, name.capitalize.some)(using ErrorPolicy.RaiseAlways)
                       .withInput((orcidId, fallbackProfile))
              _ <- Logger[F].debug(s"$name result: $u")
            yield u

          override def canonicalizePreAuthUser(
            orcidId:         OrcidId,
            fallbackProfile: UserProfile
          ): F[User] =
            go(CanonicalizePreAuthUser, "canonicalizePreAuthUser", orcidId, fallbackProfile)

          override def updateFallback(
            orcidId:         OrcidId,
            fallbackProfile: UserProfile
          ): F[Option[User]] =
            go(UpdateFallback, "updateFallback", orcidId, fallbackProfile)

trait SsoGraphQlClientOps:
  trait PreAuthUserOperation extends GraphQLOperation[Unit]:
    type Variables = (OrcidId, UserProfile)

    override val varEncoder: Encoder.AsObject[Variables] =
      Encoder.AsObject.instance[Variables]: input =>
        JsonObject(
          "orcidId"         -> input._1.asJson,
          "fallbackProfile" -> input._2.asJson
        )

    // The `User` codec in `lucuma.sso.client.codec.user` doesn't correspond
    // to the schema so we'll make a decoder here for the special case of
    // creating a pre-auth standard user.
    given Decoder[User] =
      (c: HCursor) =>
        for
          id <- c.get[User.Id]("id")
          oi <- c.get[OrcidId]("orcidId")
          pp <- c.get[UserProfile]("primaryProfile")
          fp <- c.get[UserProfile]("fallbackProfile")
          sr <- c.downField("roles").downArray.downField("id").as[StandardRole.Id]
        yield StandardUser(id, StandardRole.Pi(sr), Nil, OrcidProfile(oi, pp, fp))

    override val document: String =
      """
        fragment UserProfileFields on UserProfile {
          givenName
          familyName
          creditName
          email
        }

        fragment UserFields on User {
          id
          orcidId
          primaryProfile {
            ...UserProfileFields
          }
          fallbackProfile {
            ...UserProfileFields
          }
          roles {
            id
            type
          }
        }

        mutation CanonicalizePreAuthUser($orcidId: OrcidId!, $fallbackProfile: UserProfileInput!) {
          canonicalizePreAuthUser(orcidId: $orcidId, fallbackProfile: $fallbackProfile) {
            ...UserFields
          }
        }

        mutation UpdateFallback($orcidId: OrcidId!, $fallbackProfile: UserProfileInput!) {
          updateFallback(orcidId: $orcidId, fallbackProfile: $fallbackProfile) {
            ...UserFields
          }
        }
      """

  object CanonicalizePreAuthUser extends PreAuthUserOperation:
    type Data = User

    override val dataDecoder: Decoder[User] =
      (c: HCursor) => c.get[User]("canonicalizePreAuthUser")

  object UpdateFallback extends PreAuthUserOperation:
    type Data = Option[User]

    override val dataDecoder: Decoder[Option[User]] =
      (c: HCursor) => c.get[Option[User]]("updateFallback")