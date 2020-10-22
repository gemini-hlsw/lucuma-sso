// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.client

import cats._
import cats.data._
import cats.effect.Sync
import cats.syntax.all._
import java.security.PublicKey
import lucuma.core.model.User
import lucuma.sso.client.util.JwtDecoder
import org.http4s._
import org.http4s.dsl.Http4sDsl
import pdi.jwt.exceptions._
import io.chrisdavenport.log4cats.Logger

object SsoMiddleware {

  def apply[F[_]: Sync: Logger](
    publicKey: PublicKey
  ): AuthedRoutes[User, F] => HttpRoutes[F] =
    apply(SsoJwtReader(JwtDecoder.withPublicKey[F](publicKey)))

  def apply[F[_]: Monad: Logger](
    jwtReader: SsoJwtReader[F]
  ): AuthedRoutes[User, F] => HttpRoutes[F] = rs =>
    Kleisli { req =>
      val dsl = Http4sDsl[F]; import dsl._
      OptionT {
        jwtReader.attemptFindClaim(req).flatMap {
          case Some(Right(jwt)) =>
            jwt.getUser match {
              case Right(u) => Logger[F].debug(s"User is: ${u}") *> rs(ContextRequest(u, req)).value
              case Left(_)  => BadRequest("JWT is valid but user struct is unreadable.").map(_.pure[Option])
            }
          case Some(Left(e)) =>
            BadRequest(
              e match {
                case _: JwtLengthException               => "JwtLengthException"
                case _: JwtValidationException           => "JwtValidationException"
                case _: JwtSignatureFormatException      => "JwtSignatureFormatException"
                case _: JwtEmptySignatureException       => "JwtEmptySignatureException"
                case _: JwtNonEmptySignatureException    => "JwtNonEmptySignatureException"
                case _: JwtEmptyAlgorithmException       => "JwtEmptyAlgorithmException"
                case _: JwtNonEmptyAlgorithmException    => "JwtNonEmptyAlgorithmException"
                case _: JwtExpirationException           => "JwtExpirationException"
                case _: JwtNotBeforeException            => "JwtNotBeforeException"
                case _: JwtNonSupportedAlgorithm         => "JwtNonSupportedAlgorithm"
                case _: JwtNonSupportedCurve             => "JwtNonSupportedCurve"
                case _: JwtNonStringException            => "JwtNonStringException"
                case _: JwtNonStringSetOrStringException => "JwtNonStringSetOrStringException"
                case _: JwtNonNumberException            => "JwtNonNumberException"
              }
            ).map(_.pure[Option])
          case None =>
            Forbidden().map(_.pure[Option])
        }
      }
    }

}