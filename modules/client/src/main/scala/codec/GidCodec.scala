// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client.codec

import eu.timepit.refined.types.numeric.PosLong
import io.circe.{ Encoder, Decoder }
import io.circe.refined._
import lucuma.core.util.Gid

trait GidCodec {

  implicit def gidEncoder[A](implicit ev: Gid[A]): Encoder[A] =
    Encoder[PosLong].contramap(ev.isoPosLong.get)

  implicit def gidDecoder[A](implicit ev: Gid[A]): Decoder[A] =
    Decoder[PosLong].map(ev.isoPosLong.reverseGet)

}

object gid extends GidCodec