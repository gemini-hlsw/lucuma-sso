// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.database

import lucuma.core.util.Enumerated

// lucuma_role_type
sealed trait RoleType extends Product with Serializable

object RoleType {
  case object Pi    extends RoleType
  case object Ngo   extends RoleType
  case object Staff extends RoleType
  case object Admin extends RoleType

  implicit val roleType: Enumerated[RoleType] =
    Enumerated.of(Pi, Ngo, Staff, Admin)

}
