// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.database

import lucuma.sso.model._
import skunk._
import skunk.codec.all._
import skunk.data.Type
import lucuma.core.util.Enumerated

// Codecs for some atomic types.
trait Codecs {

  val orcid: Codec[Orcid] =
    Codec.simple[Orcid](
      _.value,
      Orcid.fromString(_).toRight("Invalid ORCID iD"),
      Type.varchar
    )

  val user_id: Codec[User.Id] = {
    val prism = Gid[User.Id].fromString
    Codec.simple(
      prism.reverseGet,
      s => prism.getOption(s).toRight(s"Invalid user id: $s"),
      Type.varchar
    )
  }

  val role_id: Codec[StandardRole.Id] = {
    val prism = Gid[StandardRole.Id].fromString
    Codec.simple(
      prism.reverseGet,
      s => prism.getOption(s).toRight(s"Invalid role id: $s"),
      Type.varchar
    )
  }

  val role_type: Codec[RoleType] =
    enum(RoleType, Type("lucuma_role_type"))

  val partner: Codec[Partner] =
    enum[Partner](Enumerated[Partner].tag, Enumerated[Partner].fromTag, Type("lucuma_ngo"))

}