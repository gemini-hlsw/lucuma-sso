package gpp.sso.service.database

import enumeratum._
import enumeratum.EnumEntry.Lowercase

// gpp_role_type
sealed trait RoleType extends EnumEntry with Lowercase
object RoleType extends Enum[RoleType] {
  case object Pi    extends RoleType
  case object Ngo   extends RoleType
  case object Staff extends RoleType
  case object Admin extends RoleType
  val values = findValues
}
