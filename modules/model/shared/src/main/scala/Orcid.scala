// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.model

import cats.Eq
import cats.implicits._
import io.circe._

sealed abstract case class Orcid(value: String)
object Orcid {

  private val Pat = """^(\d{4}-){3,}\d{3}[\dX]$""".r

  def fromString(s: String): Option[Orcid] =
    Pat.findFirstIn(s).map(new Orcid(_) {})

  implicit val EqOrcid: Eq[Orcid] =
    Eq.by(_.value)

  implicit val encoder: Encoder[Orcid] =
    Encoder[String].contramap(_.value)

  implicit val decoder: Decoder[Orcid] =
    Decoder[String].emap(s => fromString(s).toRight(s"Invalid ORCID iD: $s"))

}
