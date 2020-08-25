// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.model

import cats._
import cats.implicits._
import io.circe._
import monocle.{ Iso, Prism }
import scala.util.matching.Regex

/**
 * A typeclass for lucuma identifiers, which are of the form T-26fd21b3 where T is a constant,
 * type-specific tag and the remainder is a positive hex-encoded Long, with lowercase alpha digits
 * and no leading zeros (thus having a unique string representation).
 */
final class Gid[A](
  val tag:     Byte,
  val isoLong: Iso[A, Long]
) extends Order[A]
     with Show[A]
     with Encoder[A]
     with Decoder[A] {

  // We use this in a few places
  private final val TagString: String = tag.toChar.toString

  /** Gids have a canonical String representation. */
  final val fromString: Prism[String, A] = {

    val R: Regex =
      raw"^$TagString-(0|[1-9a-f][0-9a-f]*)$$".r

    def parse(s: String): Option[A] =
      s match {
        case R(n) => Either.catchOnly[NumberFormatException] {
            isoLong.reverseGet(java.lang.Long.parseLong(n, 16))
          } .toOption
        case _ => None
      }

    def show(a: A): String =
      s"$TagString-${isoLong.get(a).toHexString}"

    Prism(parse)(show)

  }

  // Show
  final override def show(a: A): String =
    fromString.reverseGet(a)

  // Order
  final override def compare(a: A, b: A): Int =
    isoLong.get(a) compare isoLong.get(b)

  // Encoder
  final override def apply(a: A): Json =
    Json.fromString(fromString.reverseGet(a))

  // Decoder
  def apply(c: HCursor): Decoder.Result[A] =
    c.as[String].flatMap { s =>
      fromString
        .getOption(s)
        .toRight(DecodingFailure(s"invalid: $s", c.history))
    }

}

object Gid {

  def apply[A](implicit ev: Gid[A]): ev.type = ev

  def instance[A](tag: Byte, toLong: A => Long, fromLong: Long => A): Gid[A] =
    new Gid[A](tag, Iso(toLong)(fromLong))
}
