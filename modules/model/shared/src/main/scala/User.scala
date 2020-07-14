package gpp.sso.model

import cats.implicits._

/**
  * @param givenName   The given name(s) of the researcher or contributor.
  * @param familyName  The family (last) name of the researcher. This element is optional, because some cultures only use given names.
  * @param creditName  The full name of the researcher as they prefer it to appear.
  * @param currentRole The user's current role. Other roles may be present.
  * @param otherRoles  Other roles available to the user. SSO provides an API for switching roles.
  */
final case class User(
  orcidId:     Orcid.Id,
  givenName:   Option[String],
  familyName:  Option[String],
  creditName:  Option[String],
  currentRole: Role,
  otherRoles:  List[Role]
) {

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
  ).getOrElse(orcidId.value)

}

