// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.graphql

import grackle.skunk.SkunkMapping
import io.circe
import io.circe.Json
import lucuma.core.util.Enumerated
import lucuma.sso.service.database.Codecs
import lucuma.sso.service.database.RoleType
import skunk.codec.all.*

trait SsoTables[F[_]] extends Codecs:
  this: SkunkMapping[F] =>

  implicit val RoleTypeEncoder: circe.Encoder[RoleType] =
    rt => Json.fromString(Enumerated[RoleType].tag(rt).toUpperCase())

  object User extends TableDef("lucuma_user"):
    val Id = col("user_id", user_id)
    val OrcidId = col("orcid_id", orcid_id)

    trait Profile:
      def GivenName: ColumnRef
      def FamilyName: ColumnRef
      def CreditName: ColumnRef
      def Email: ColumnRef

    object Primary extends Profile:
      override val GivenName = col("orcid_given_name", varchar.opt)
      override val FamilyName = col("orcid_family_name", varchar.opt)
      override val CreditName = col("orcid_credit_name", varchar.opt)
      override val Email = col("orcid_email", varchar.opt)

    object Fallback extends Profile:
      override val GivenName = col("fallback_given_name", varchar.opt)
      override val FamilyName = col("fallback_family_name", varchar.opt)
      override val CreditName = col("fallback_credit_name", varchar.opt)
      override val Email = col("fallback_email", varchar.opt)

  object Role extends TableDef("lucuma_role"):
    val Id = col("role_id", role_id)
    val Type = col("role_type", role_type)
    val Partner = col("role_ngo", partner.opt)
    val UserId = col("user_id", user_id)

  object ApiKey extends TableDef("lucuma_api_key"):
    val Id = col("api_key_id", varchar)
    val UserId = col("user_id", user_id)
    val RoleId = col("role_id", role_id)