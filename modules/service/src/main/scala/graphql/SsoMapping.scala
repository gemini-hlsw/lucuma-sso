// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.graphql

import _root_.skunk.Channel
import _root_.skunk.Session
import _root_.skunk.implicits._
import cats.data.NonEmptyChain
import cats.effect.{Unique => _, _}
import cats.syntax.all._
import edu.gemini.grackle.Path._
import edu.gemini.grackle.Predicate._
import edu.gemini.grackle.Query._
import edu.gemini.grackle.QueryCompiler
import edu.gemini.grackle.Schema
import edu.gemini.grackle._
import edu.gemini.grackle.skunk.SkunkMapping
import edu.gemini.grackle.skunk.SkunkMonitor
import eu.timepit.refined.types.numeric.PosLong
import fs2.Stream
import lucuma.core.model
import lucuma.core.model.OrcidId
import lucuma.core.model.Partner
import lucuma.core.model.StandardRole
import lucuma.core.model.StandardUser
import lucuma.sso.client.ApiKey
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
        Schema(src.mkString).right.get // TODO
      } .liftTo[F]
    }

  def apply[F[_]: Async: Trace](
    channels: Channels[F],
    pool:     Resource[F, Session[F]],
    monitor:  SkunkMonitor[F],
  ): F[StandardUser => Mapping[F]] =
    loadSchema[F].map { loadedSchema => user =>

      // Directly-computed result for `createApiKey` mutation.
      def createApiKey(env: Cursor.Env): F[Result[String]] =
        env.get[StandardRole.Id]("roleId") match {
          case Some(roleId) =>
              if ((user.role :: user.otherRoles).exists(_.id === roleId))
                pool.map(Database.fromSession(_)).use { db =>
                  db.createApiKey(roleId)
                    .map(apiKey => ApiKey.fromString.reverseGet(apiKey).rightIor[NonEmptyChain[Problem]])
                }
              else
                Problem(show"No such role: $roleId").leftIorNec.pure[F]
          case None =>
            Problem(s"Implementation error: `roleId` is not in $env").leftIorNec.pure[F]
        }

      def deleteApiKey(env: Cursor.Env): F[Result[Boolean]] =
        env.get[PosLong]("id") match {
          case Some(id) =>
            pool.map(Database.fromSession(_)).use { db =>
              db.deleteApiKey(id, Some(user.id)).map(_.rightIor[NonEmptyChain[Problem]])
            }
          case None =>
            Problem(s"Implementation error: `id` is not in $env").leftIorNec.pure[F]
        }

      val apiKeyRevocation: Stream[F, Result[String]] =
        channels
          .apiKeyDeletions
          .listen(1024)
          .evalTap(n => Async[F].delay(println(n)))
          .map(_.value.rightIor)

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

        val typeMappings: List[TypeMapping] =
          List(
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
                RootEffect.computeEncodable("createApiKey")((_,_,e) => createApiKey(e)),
                RootEffect.computeEncodable("deleteApiKey")((_,_,e) => deleteApiKey(e))
              )
            ),
            ObjectMapping(
              tpe = UserType,
              fieldMappings = List(
                SqlField("id", User.Id, key = true),
                SqlField("orcidId", User.OrcidId),
                SqlField("givenName", User.GivenName),
                SqlField("familyName", User.FamilyName),
                SqlField("creditName", User.CreditName),
                SqlField("email", User.Email),
                SqlObject("roles", Join(User.Id, Role.UserId)),
                SqlObject("apiKeys", Join(User.Id, ApiKey.UserId)),
              )
            ),
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
                RootEffect.computeEncodableStream("apiKeyRevocation")((_,_,_) => apiKeyRevocation)
              )
            ),
            LeafMapping[model.User.Id](UserIdType),
            LeafMapping[OrcidId](OrcidIdType),
            LeafMapping[StandardRole.Id](RoleIdType),
            LeafMapping[RoleType](RoleTypeType),
            LeafMapping[Partner](PartnerType),
            LeafMapping[String](ApiKeyIdType),
          )

        override val selectElaborator = new QueryCompiler.SelectElaborator(Map(
          QueryType -> {
            case Select("user", Nil, child) =>
              Select("user", Nil, Unique(Filter(Eql(UserType / "id", Const(user.id)), child))).rightIor
            case Select("role", Nil, child) =>
              Select("role", Nil, Unique(Filter(Eql(UserType / "id", Const(user.role.id)), child))).rightIor
          },
          MutationType -> {
            case Select("createApiKey", List(Binding("role", Value.StringValue(id))), child) =>
              StandardRole.Id.parse(id).toRightIorNec(Problem(s"Not a valid role id: $id")).map { roleId =>
                Environment(
                  Cursor.Env("roleId" -> roleId),
                  Select("createApiKey", Nil, child)
                )
              }
            case Select("deleteApiKey", List(Binding("id", Value.StringValue(hexString))), child) =>
              lucuma.sso.client.ApiKey.Id.fromString.getOption(hexString).toRightIorNec(Problem(s"Not a valid API key id: $hexString")).map { keyId =>
                Environment(
                  Cursor.Env("id" -> keyId),
                  Select("deleteApiKey", Nil, child)
                )
              }
          },
        ))

      }

    }
}
