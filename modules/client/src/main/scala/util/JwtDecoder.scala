// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.client.util

import cats.ApplicativeError
import cats.implicits._
import java.security.PublicKey
import pdi.jwt.{ Jwt, JwtClaim }

/** Service for a JWT client that needs to verify tokens. */
trait JwtDecoder[F[_]] {

  /** Attept to decode the given token, raising an error in F on failuer. */
  def decode(token: String): F[JwtClaim]
}

object JwtDecoder {

  /**
   * Construct a JwtDecoder that will decode signed tokens and validate them using the provided
   * JCA `PublicKey`.
   */
  def withPublicKey[F[_]: ApplicativeError[*[_], Throwable]](
    pub: PublicKey
  ): JwtDecoder[F] =
    new JwtDecoder[F] {
      def decode(token: String): F[JwtClaim] =
        Jwt.decode(token, pub).liftTo[F]
    }


}