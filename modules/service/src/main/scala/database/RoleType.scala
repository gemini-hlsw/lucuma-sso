// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.database

import lucuma.core.util.Enumerated

// lucuma_role_type
enum RoleType(private val tag: String) derives Enumerated:
  case Pi extends RoleType("pi")
  case Ngo extends RoleType("ngo")
  case Staff extends RoleType("staff")
  case Admin extends RoleType("admin")
