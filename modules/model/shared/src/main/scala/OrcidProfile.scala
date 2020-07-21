// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.model

import cats.implicits._

final case class OrcidProfile(
  orcid:        Orcid,
  givenName:    Option[String],
  familyName:   Option[String],
  creditName:   Option[String],
  primaryEmail: String,
) {

  def displayName: String = (
    creditName <+> (givenName, familyName).mapN((g, f) => s"$g $f") <+> familyName <+> givenName
  ).getOrElse(orcid.value)

}