// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.graphql

import skunk._
import cats.effect._
import cats.syntax.all._
import edu.gemini.grackle.Predicate._
import edu.gemini.grackle.Query._
import edu.gemini.grackle.QueryCompiler
import lucuma.core.model
import lucuma.core.model.OrcidId
import lucuma.core.model.Partner
import lucuma.sso.service.database.RoleType
import edu.gemini.grackle.skunk.SkunkMonitor
import edu.gemini.grackle.skunk.SkunkMapping
import edu.gemini.grackle.Schema
import scala.io.Source
import scala.util.Using
import lucuma.core.model.StandardUser

object SsoMapping {

  // In principle this is a pure operation because resources are constant values, but the potential
  // for error in dev is high and it's nice to handle failures in `F`.
  def loadSchema[F[_]: Sync]: F[Schema] =
    Sync[F].defer {
      Using(Source.fromResource("Sso.graphql", getClass().getClassLoader())) { src =>
        Schema(src.mkString).right.get // TODO
      } .liftTo[F]
    }

  def apply[F[_]: Sync](
    pool:    Resource[F, Session[F]],
    monitor: SkunkMonitor[F],
  ): F[StandardUser => SkunkMapping[F]] =
    loadSchema[F].map { loadedSchema => user =>

      new SkunkMapping[F](pool, monitor) with SsoTables[F] {

        val schema: Schema = loadedSchema

        val ApiKeyType   = schema.ref("ApiKey")
        val QueryType    = schema.ref("Query")
        val UserType     = schema.ref("User")
        val UserIdType   = schema.ref("UserId")
        val OrcidIdType  = schema.ref("OrcidId")
        val RoleType     = schema.ref("Role")
        val RoleIdType   = schema.ref("RoleId")
        val RoleTypeType = schema.ref("RoleType")
        val PartnerType  = schema.ref("Partner")

        val typeMappings: List[TypeMapping] =
          List(
            ObjectMapping(
              tpe = QueryType,
              fieldMappings = List(
                SqlRoot("user"),
                SqlRoot("role"),
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
                SqlAttribute("«unused»", Role.UserId),
                SqlObject("user", Join(Role.UserId, User.Id)),
              )
            ),
            ObjectMapping(
              tpe = ApiKeyType,
              fieldMappings = List(
                SqlField("id", ApiKey.Id, key = true),
                SqlAttribute("«unused»", ApiKey.UserId),
                SqlAttribute("«unused»", ApiKey.RoleId),
                SqlObject("user", Join(ApiKey.UserId, User.Id)),
                SqlObject("role", Join(ApiKey.RoleId, Role.Id)),
              )
            ),
            LeafMapping[model.User.Id](UserIdType),
            LeafMapping[OrcidId](OrcidIdType),
            LeafMapping[model.StandardRole.Id](RoleIdType),
            LeafMapping[RoleType](RoleTypeType),
            LeafMapping[Partner](PartnerType)
          )

        override val selectElaborator = new QueryCompiler.SelectElaborator(Map(
          QueryType -> {
            case Select("user", Nil, child) =>
              Select("user", Nil, Unique(Eql(FieldPath(List("id")), Const(user.id)), child)).rightIor
            case Select("role", Nil, child) =>
              Select("role", Nil, Unique(Eql(FieldPath(List("id")), Const(user.role.id)), child)).rightIor
          }
        ))

      }

    }
}