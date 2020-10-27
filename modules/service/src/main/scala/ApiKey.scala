// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect.Sync
import cats.Eq
import cats.implicits._
import eu.timepit.refined._
import eu.timepit.refined.cats._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.types.numeric.PosLong
import monocle.Prism
import org.http4s.{ DecodeResult, EntityDecoder, EntityEncoder, MalformedMessageBodyFailure }
import scala.util.matching.Regex
import org.http4s.QueryParamDecoder
import org.http4s.ParseFailure

/**
 * An API key consists of an id (a positive Long) and a cleartext body (a 96-char lowecase hex
 * string). API keys have a canonical string representation `id.body` where `id` is in lowercase
 * hex, for example:
 * {{{
 * 10d.3b9e2adc5bffdac72f487a2760061bcfebc44037b69f2085c9a1ba10aa5d2d338421fc0d79f45cfd07666617ac4e2c89
 * }}}
 */
final case class ApiKey(id: PosLong, body: String)
object ApiKey {

  private val R: Regex =
    raw"^([0-9a-f]{3,})\.([0-9a-f]{96})$$".r

  val fromString: Prism[String, ApiKey] =
    Prism[String, ApiKey] {
      case R(sid, body) =>
        for {
          signed <- Either.catchOnly[NumberFormatException](java.lang.Long.parseLong(sid, 16)).toOption
          pos    <- refineV[Positive](signed).toOption
        } yield ApiKey(pos, body)
      case _ => None
     } { k =>
      s"${k.id.value.toHexString}.${k.body}"
    }

  implicit val EqSessionToken: Eq[ApiKey] =
    Eq.by(k => (k.id, k.body))

  implicit def entityEncoder[F[_]]: EntityEncoder[F, ApiKey] =
    EntityEncoder[F, String].contramap(fromString.reverseGet)

  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, ApiKey] =
    EntityDecoder[F, String].map(fromString.getOption).flatMapR {
      case Some(ak) => DecodeResult.success(ak)
      case None     => DecodeResult.failure(MalformedMessageBodyFailure("Invalid API Key"))
    }

  implicit val queryParamDecoder: QueryParamDecoder[ApiKey] =
    QueryParamDecoder[String]
      .map(fromString.getOption)
      .emap(_.toRight(ParseFailure("<redacted>", "Invalid API Key")))

}