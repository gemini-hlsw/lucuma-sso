// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.graphql

import _root_.skunk.Channel
import _root_.skunk.Session
import _root_.skunk.implicits.*
import cats.effect.{Unique as _, *}
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosLong
import fs2.Stream
import grackle.*
import grackle.Predicate.*
import grackle.Query.*
import grackle.QueryCompiler
import grackle.QueryCompiler.Elab
import grackle.QueryCompiler.SelectElaborator
import grackle.Schema
import grackle.skunk.SkunkMapping
import grackle.skunk.SkunkMonitor
import grackle.syntax.*
import lucuma.core.enums.Partner
import lucuma.core.model
import lucuma.core.model.Access
import lucuma.core.model.OrcidId
import lucuma.core.model.ServiceUser
import lucuma.core.model.StandardRole
import lucuma.core.model.StandardUser
import lucuma.core.model.User
import lucuma.core.model.UserProfile
import lucuma.sso.service.database.Database
import lucuma.sso.service.database.RoleType
import natchez.Trace

import scala.io.Source
import scala.util.Using

object SsoMapping {

  case class Channels[F[_]](
    apiKeyDeletions: Channel[F, String, String]
  )

  object Channels {
    def apply[F[_]](pool: Resource[F, Session[F]]): Resource[F, Channels[F]] =
      pool.map { s =>
        Channels(
          apiKeyDeletions = s.channel(id"lucuma_api_key_deleted")
        )
      }
  }

  // In principle this is a pure operation because resources are constant values, but the potential
  // for error in dev is high and it's nice to handle failures in `F`.
  def loadSchema[F[_]: Sync]: F[Schema] =
    Sync[F].defer {
      Using(Source.fromResource("Sso.graphql", getClass().getClassLoader())) { src =>
        Schema(src.mkString).toOption.get // TODO
      } .liftTo[F]
    }

  def apply[F[_]: Async: Trace](
    channels: Channels[F],
    pool:     Resource[F, Session[F]],
    monitor:  SkunkMonitor[F],
  ): F[StandardUser | ServiceUser => Mapping[F]] =
    loadSchema[F].map { loadedSchema => user =>

      def requireStaffAccess[A](a: => F[Result[A]]): F[Result[A]] =
        if user.role.access >= Access.Staff then a
        else Result.failure("Staff access required for this operation.").pure[F]

      def requireStandardUser[A](f: StandardUser => F[Result[A]]): F[Result[A]] =
        user match
          case su@StandardUser(_, _, _, _) => f(su)
          case _                           => Result.failure("A standard user is required for this operation.").pure[F]

      // Directly-computed result for `createApiKey` mutation.
      def createApiKey(env: Env): F[Result[String]] =
        requireStandardUser: su =>
          env
            .getR[StandardRole.Id]("roleId")
            .flatTraverse: roleId =>
              if (su.role :: su.otherRoles).exists(_.id === roleId) then
                pool.map(Database.fromSession(_)).use: db =>
                  db.createApiKey(roleId)
                    .map(apiKey => Result(lucuma.sso.client.ApiKey.fromString.reverseGet(apiKey)))
              else
                Result.failure(show"No such role: $roleId").pure[F]

      def deleteApiKey(env: Env): F[Result[Boolean]] =
        requireStandardUser: su =>
          env
            .getR[PosLong]("id")
            .flatTraverse: id =>
              pool.map(Database.fromSession(_)).use: db =>
                db.deleteApiKey(id, Some(su.id)).map(Result.success)

      val apiKeyRevocation: Stream[F, Result[String]] =
        channels
          .apiKeyDeletions
          .listen(1024)
          .evalTap(n => Async[F].delay(println(n)))
          .map(a => Result.success(a.value))

      def canonicalizePreAuthUser(env: Env): F[Result[User.Id]] =
        requireStaffAccess:
          (
            env.getR[OrcidId]("orcidId"),
            env.getR[UserProfile]("fallbackProfile")
          ).tupled.flatTraverse: (orcid, fallback) =>
            pool.map(Database.fromSession(_)).use: db =>
              db.canonicalizePreAuthUser(orcid, fallback).map(Result.success)

      def updateFallback(env: Env): F[Result[Option[User.Id]]] =
        requireStaffAccess:
          (
            env.getR[OrcidId]("orcidId"),
            env.getR[UserProfile]("fallbackProfile")
          ).tupled.flatTraverse: (orcid, fallback) =>
            pool.map(Database.fromSession(_)).use: db =>
              db.updateFallback(orcid, fallback).map(Result.success)

      new SkunkMapping[F](pool, monitor) with SsoTables[F] {

        val schema: Schema = loadedSchema

        val ApiKeyIdType     = schema.ref("ApiKeyId")
        val ApiKeyType       = schema.ref("ApiKey")
        val MutationType     = schema.ref("Mutation")
        val OrcidIdType      = schema.ref("OrcidId")
        val PartnerType      = schema.ref("Partner")
        val QueryType        = schema.ref("Query")
        val RoleIdType       = schema.ref("RoleId")
        val RoleType         = schema.ref("Role")
        val RoleTypeType     = schema.ref("RoleType")
        val SubscriptionType = schema.ref("Subscription")
        val UserIdType       = schema.ref("UserId")
        val UserType         = schema.ref("User")
        val UserProfileType  = schema.ref("UserProfile")

        def profileMapping(path: Path, p: User.Profile): ObjectMapping =
          ObjectMapping(path)(
            SqlField("synthetic-id", User.Id, key = true, hidden = true),
            SqlField("givenName",  p.GivenName),
            SqlField("familyName", p.FamilyName),
            SqlField("creditName", p.CreditName),
            SqlField("email",      p.Email)
          )

        val typeMappings: TypeMappings =
          TypeMappings(
            List[TypeMapping](
              ObjectMapping(
                tpe = QueryType,
                fieldMappings = List(
                  SqlObject("user"),
                  SqlObject("role"),
                )
              ),
              ObjectMapping(
                tpe = MutationType,
                fieldMappings = List(
                  RootEffect.computeEncodable("createApiKey")((_, e) => createApiKey(e)),
                  RootEffect.computeEncodable("deleteApiKey")((_, e) => deleteApiKey(e)),
                  RootEffect.computeChild("canonicalizePreAuthUser") { (child, _, env) =>
                    canonicalizePreAuthUser(env).map: result =>
                      result.map: uid =>
                        Unique(Filter(Eql(UserType / "id", Const(uid)), child))
                  },
                  RootEffect.computeChild("updateFallback") { (child, _, env) =>
                    updateFallback(env).map: result =>
                      result.map: ouid =>
                        ouid.fold(Filter(False, child))(uid => Unique(Filter(Eql(UserType / "id", Const(uid)), child)))
                  }
                )
              ),
              ObjectMapping(
                tpe = UserType,
                fieldMappings = List(
                  SqlField("id", User.Id, key = true),
                  SqlField("orcidId", User.OrcidId),
                  SqlObject("primaryProfile"),
                  SqlObject("fallbackProfile"),
                  SqlObject("roles",   Join(User.Id, Role.UserId)),
                  SqlObject("apiKeys", Join(User.Id, ApiKey.UserId)),
                )
              ),
              profileMapping(UserType / "primaryProfile",  User.Primary),
              profileMapping(UserType / "fallbackProfile", User.Fallback),
              ObjectMapping(
                tpe = RoleType,
                fieldMappings = List(
                  SqlField("id", Role.Id, key = true),
                  SqlField("type", Role.Type),
                  SqlField("partner", Role.Partner),
                  SqlField("«unused»", Role.UserId, hidden = true),
                  SqlObject("user", Join(Role.UserId, User.Id)),
                )
              ),
              ObjectMapping(
                tpe = ApiKeyType,
                fieldMappings = List(
                  SqlField("id", ApiKey.Id, key = true),
                  SqlField("«unused»", ApiKey.UserId, hidden = true),
                  SqlField("«unused»", ApiKey.RoleId, hidden = true),
                  SqlObject("user", Join(ApiKey.UserId, User.Id)),
                  SqlObject("role", Join(ApiKey.RoleId, Role.Id)),
                )
              ),
              ObjectMapping(
                tpe = SubscriptionType,
                fieldMappings = List(
                  RootStream.computeEncodable("apiKeyRevocation")((_,_) => apiKeyRevocation)
                )
              ),
              LeafMapping[model.User.Id](UserIdType),
              LeafMapping[OrcidId](OrcidIdType),
              LeafMapping[StandardRole.Id](RoleIdType),
              LeafMapping[RoleType](RoleTypeType),
              LeafMapping[Partner](PartnerType),
              LeafMapping[String](ApiKeyIdType),
            )
          )

        object UserProfileInput:
          val Binding: Matcher[UserProfile] =
            ObjectFieldsBinding.rmap {
              case List(
                StringBinding.Option("givenName",  rGiven),
                StringBinding.Option("familyName", rFamily),
                StringBinding.Option("creditName", rCredit),
                StringBinding.Option("email",      rEmail)
              ) => (rGiven, rFamily, rCredit, rEmail).parMapN(UserProfile.apply)
            }

        val OrcidIdBinding: Matcher[OrcidId] =
          StringBinding.emap(OrcidId.fromValue)

        override val selectElaborator = SelectElaborator {

          case (QueryType, "user", Nil) =>
            Elab.transformChild(c => Unique(Filter(Eql(UserType / "id", Const(user.id)), c)))

          case (QueryType, "role", Nil) =>
            user match
              case StandardUser(_, role, _, _) =>
                Elab.transformChild(c => Unique(Filter(Eql(UserType / "id", Const(role.id)), c)))
              case _                           =>
                Elab.failure("'role' requires a standard user.")

          case (MutationType, "createApiKey", List(Binding("role", Value.StringValue(id)))) =>
            val rRoleId = Result.fromOption(StandardRole.Id.parse(id), s"Not a valid role id: $id")
            Elab.liftR(rRoleId).flatMap { roleId =>  Elab.env("roleId" -> roleId) }

          case (MutationType, "deleteApiKey", List(Binding("id", Value.StringValue(hexString)))) =>
            val rKeyId = Result.fromOption(lucuma.sso.client.ApiKey.Id.fromString.getOption(hexString), s"Not a valid API key id: $hexString")
            Elab.liftR(rKeyId).flatMap { keyId => Elab.env("id" -> keyId)}

          case (MutationType, "canonicalizePreAuthUser", List(
            OrcidIdBinding("orcidId", rOrcidId),
            UserProfileInput.Binding("fallbackProfile", rFallback))
          ) =>
            Elab.liftR((rOrcidId, rFallback).tupled).flatMap: (orcid, fallback) =>
              Elab.env("orcidId" -> orcid, "fallbackProfile" -> fallback)

          case (MutationType, "updateFallback", List(
            OrcidIdBinding("orcidId", rOrcidId),
            UserProfileInput.Binding("fallbackProfile", rFallback))
          ) =>
            Elab.liftR((rOrcidId, rFallback).tupled).flatMap: (orcid, fallback) =>
              Elab.env("orcidId" -> orcid, "fallbackProfile" -> fallback)
        }

      }

    }
}
