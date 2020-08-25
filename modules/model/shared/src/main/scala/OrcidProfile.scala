// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.model

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._

final case class OrcidProfile(
  orcid:        Orcid,
  givenName:    Option[String],
  familyName:   Option[String],
  creditName:   Option[String],
  primaryEmail: String,
) {

  def displayName: String =
    OrcidProfile.displayName(givenName, familyName, creditName).getOrElse(orcid.value)

}

object OrcidProfile {

  // N.B. this is reused by OrcidPerson, otherwise it would be inlined above
  def displayName(
    givenName:    Option[String],
    familyName:   Option[String],
    creditName:   Option[String],
  ): Option[String] =
    creditName <+> (givenName, familyName).mapN((g, f) => s"$g $f") <+> familyName <+> givenName

  implicit val encoder: Encoder[OrcidProfile] = deriveEncoder
  implicit val decoder: Decoder[OrcidProfile] = deriveDecoder

}