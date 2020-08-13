// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.model

import cats.implicits._
import io.circe._
import io.circe.syntax._

/** Each `Role` has [at least] an `Access`. */
sealed abstract class Role(val access: Access, elaboration: Option[String] = None) {
  final def name = elaboration.foldLeft(access.name)((n, e) => s"$n ($e)")
}

// Special roles
final case object GuestRole extends Role(Access.Guest)
final case class  ServiceRole(serviceName: String) extends Role(Access.Service, Some(serviceName))

// Standard roles
sealed abstract class StandardRole(access: Access, elaboration: Option[String] = None) extends Role(access, elaboration) {
  def id: StandardRole.Id
}
object StandardRole {

  final case class Pi(id: StandardRole.Id) extends StandardRole(Access.Pi)
  final case class Ngo(id: StandardRole.Id, partner: Partner) extends StandardRole(Access.Ngo, Some(partner.name))
  final case class Staff(id: StandardRole.Id) extends StandardRole(Access.Staff)
  final case class Admin(id: StandardRole.Id) extends StandardRole(Access.Admin)

  case class Id(value: Long) {
    override def toString = this.show
  }
  object Id {
    implicit val GidId: Gid[Id] = Gid.instance('r', _.value, apply)
  }

  implicit val EncoderStandardRole: Encoder[StandardRole] = {
    case Pi(id)     => Json.obj("type" -> "pi".asJson,    "id" -> id.asJson)
    case Ngo(id, p) => Json.obj("type" -> "ngo".asJson,   "id" -> id.asJson, "partner" -> p.asJson)
    case Staff(id)  => Json.obj("type" -> "staff".asJson, "id" -> id.asJson)
    case Admin(id)  => Json.obj("type" -> "admin".asJson, "id" -> id.asJson)
  }

  implicit val DecoderStandardRole: Decoder[StandardRole] = hc =>
    (hc.downField("type").as[String], hc.downField("id").as[Id], hc.downField("partner").as[Option[Partner]])
      .tupled.flatMap {
        case ("pi",    id, None)    => Pi(id).asRight
        case ("ngo",   id, Some(p)) => Ngo(id, p).asRight
        case ("staff", id, None)    => Staff(id).asRight
        case ("admin", id, None)    => Admin(id).asRight
        case _ => DecodingFailure(s"Invalid StandardRole: ${hc.as[Json]}", hc.history).asLeft
      }

}
