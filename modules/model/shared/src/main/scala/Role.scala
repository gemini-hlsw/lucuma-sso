package gpp.sso.model

import gem.util.Enumerated
import gem.util.Display

sealed abstract class Role(
  val tpe:         Role.Type,
  val name:        String,
  val elaboration: Option[String] = None
) extends Product with Serializable

sealed abstract class AuthenticatedRole(
  tpe:         Role.Type,
  name:        String,
  elaboration: Option[String] = None
) extends Role(tpe, name, elaboration)

object Role {

  case object Guest                 extends Role(Type.Guest, "Guest")
  case object Pi                    extends AuthenticatedRole(Type.Pi,    "PI")
  case class  Ngo(partner: Partner) extends AuthenticatedRole(Type.Ngo,   "NGO", Some(partner.name))
  case object Staff                 extends AuthenticatedRole(Type.Staff, "Staff")
  case object Admin                 extends AuthenticatedRole(Type.Admin, "Admin")

  /** Roles are displayable. */
  implicit val DisplayRole: Display[Role] =
    new Display[Role] {
      def name(a: Role): String = a.name
      def elaboration(a: Role): Option[String] = a.elaboration
    }

  /** Roles have types which are enumerable. */
  sealed abstract class Type(val tag: String) extends Product with Serializable
  object Type {

    case object Guest extends Type("guest")
    case object Pi    extends Type("pi")
    case object Ngo   extends Type("ngo")
    case object Staff extends Type("staff")
    case object Admin extends Type("admin")

    implicit val EnumeratedType: Enumerated[Type] =
      new Enumerated[Type] {
        def all: List[Type] = List(Guest, Pi, Ngo, Staff, Admin) // ordered by increasing power
        def tag(a: Type): String = a.tag
      }

  }

}