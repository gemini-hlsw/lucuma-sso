// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.model

import cats.ApplicativeError
import cats.implicits._
import io.circe._
import io.circe.syntax._
import java.security.AccessControlException

/** A user has [at least] an identity and a role. */
sealed trait User extends Product with Serializable {

  def id:   User.Id
  def role: Role

  /**
   * A name to display in interfaces. This is never empty, and respects users' formatting preferences
   * as given in their ORCID record.
   */
  def displayName: String

  /** Verity that this user has access greater than or equal to `access`. */
  final def verifyAccess[F[_]](access: Access)(
    implicit ev: ApplicativeError[F, Throwable]
  ): F[Unit] =
    ev.raiseError(new AccessControlException(s"$displayName (User ${id.value}, $role) does not have required access $access."))
      .whenA(role.access < access)

}

object User {

  case class Id(value: Long) {
    override def toString = this.show
  }
  object Id {
    implicit val GidUserGid: Gid[Id] = Gid.instance('u', _.value, apply)
  }

  implicit val EncoderUser: Encoder[User] =
    Encoder.instance {
      case GuestUser(id) =>
        Json.obj(
          "type" -> "guest".asJson,
          "id"   -> id.asJson
        )
      case ServiceUser(id, name) =>
        Json.obj(
          "type" -> "service".asJson,
          "id"   -> id.asJson,
          "name" -> name.asJson,
        )
      case StandardUser(id, role, otherRoles, profile) =>
        Json.obj(
          "type"       -> "standard".asJson,
          "id"         -> id.asJson,
          "role"       -> role.asJson,
          "otherRoles" -> otherRoles.asJson,
          "profile"    -> profile.asJson,
        )

    }

  implicit val DecodeUser: Decoder[User] = hc =>
    hc.downField("type").as[String].flatMap {

      case "guest" =>
        for {
          id <- hc.downField("id").as[User.Id]
        } yield GuestUser(id)

      // TODO: others

      case tag  =>
        Left(DecodingFailure(s"Unknown user type: $tag", Nil))

    }

}


/**
 * Guest users have the lowest access and no identifying information.
 */
final case class GuestUser(id: User.Id) extends User {
  val role = GuestRole
  val displayName = "Guest User"
}

/**
 * Service users have the highest access and represent services themselves.
 */
final case class ServiceUser(id: User.Id, name: String) extends User {
  val role = ServiceRole(name)
  val displayName = s"Service User ($name)"
}

/**
 * Standard users are authenticated and have a profile, as well as a set of other roles they can
 * assume.
 */
final case class StandardUser(
  id:         User.Id,
  role:       StandardRole,
  otherRoles: List[StandardRole],
  profile:    OrcidProfile
) extends User {
  val displayName = profile.displayName
}
