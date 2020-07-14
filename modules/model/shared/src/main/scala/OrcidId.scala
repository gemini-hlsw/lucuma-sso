package gpp.sso.model

import cats.Eq
import cats.implicits._

object Orcid {

  sealed abstract case class Id(value: String)
  object Id {

    private val Pat = """^(\d{4}-){3,}\d{3}[\dX]$""".r

    def fromString(s: String): Option[Id] =
      Pat.findFirstIn(s).map(new Id(_) {})

    implicit val EqId: Eq[Id] =
      Eq.by(_.value)

  }

}