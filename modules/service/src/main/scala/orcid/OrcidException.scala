// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package orcid

package gpp.sso.service.orcid

import io.circe.Decoder
import cats.effect.Sync
import org.http4s.EntityDecoder
import org.http4s.circe._

final case class OrcidException(error: String, description: String)
  extends Exception(s"$error: $description")

object OrcidException {

  implicit val DecoderOrcidException: Decoder[OrcidException] = c =>
    for {
      error <- c.downField("error").as[String]
      desc  <- c.downField("error_description").as[String]
    } yield OrcidException(error, desc)

  implicit def entityDecoderOrcidPerson[F[_]: Sync]: EntityDecoder[F, OrcidException] =
    jsonOf[F, OrcidException]

}