package gpp.sso.model

import cats.Eq
import cats.implicits._

sealed abstract case class Orcid(value: String)
object Orcid {

  private val Pat = """^(\d{4}-){3,}\d{3}[\dX]$""".r

  def fromString(s: String): Option[Orcid] =
    Pat.findFirstIn(s).map(new Orcid(_) {})

  implicit val EqOrcid: Eq[Orcid] =
    Eq.by(_.value)

}
