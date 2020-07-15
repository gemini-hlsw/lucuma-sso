// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.model

import cats.implicits._
import java.util.UUID

sealed abstract class User {
  def uuid:        UUID
  def displayName: String
  def currentRole: Role
}

object User {

  final case class Guest(uuid: UUID) extends User {
    val currentRole = Role.Guest
    val otherRoles  = Nil
    val displayName = "Anonymous Guest"
  }

  final case class Authenticated(
    uuid:         UUID,
    orcid:        Orcid,
    givenName:    Option[String],
    familyName:   Option[String],
    creditName:   Option[String],
    primaryEmail: String,
    currentRole:  AuthenticatedRole,
    otherRoles:   List[AuthenticatedRole]
  ) extends User {

    /** Given name followed by family name, if both are known. */
    def givenAndFamilyNames: Option[String] =
      (givenName, familyName).mapN((g, f) => s"$g $f")

    /**
     * Display name, which is always defined: credit name if known; otherwise given name followed by
     * family name if both are known; otherwise given or family name if either is known; otherwise
     * the ORCID iD.
     */
    def displayName: String = (
      creditName          <+>
      givenAndFamilyNames <+>
      givenName           <+>
      familyName
    ).getOrElse(orcid.value)

  }

}
