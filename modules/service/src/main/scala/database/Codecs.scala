// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.database

import lucuma.core.model._
import lucuma.core.util.Enumerated
import lucuma.core.util.Gid
import lucuma.sso.client.ApiKey
import lucuma.sso.service.SessionToken
import skunk._
import skunk.codec.all._
import skunk.data.Type

// Codecs for some atomic types.
trait Codecs {

  val orcid_id: Codec[OrcidId] =
    Codec.simple[OrcidId](
      _.value.toString(),
      OrcidId.fromValue(_),
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

  val session_token: Codec[SessionToken] =
    uuid.gimap

  val api_key: Decoder[ApiKey] =
    text.map(ApiKey.fromString.getOption).emap(_.toRight("Invalid API Key"))

}

object Codecs extends Codecs