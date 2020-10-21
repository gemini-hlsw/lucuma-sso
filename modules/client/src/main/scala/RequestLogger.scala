// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// package lucuma.sso.client

// import cats._
// import org.http4s.HttpRoutes
// import cats.data.Kleisli
// import cats.data.OptionT
// import cats.implicits._
// import org.http4s.Request
// import cats.effect.Sync
// import pdi.jwt.exceptions.JwtLengthException
// import pdi.jwt.exceptions.JwtValidationException
// import pdi.jwt.exceptions.JwtSignatureFormatException
// import pdi.jwt.exceptions.JwtEmptySignatureException
// import pdi.jwt.exceptions.JwtNonEmptySignatureException
// import pdi.jwt.exceptions.JwtEmptyAlgorithmException
// import pdi.jwt.exceptions.JwtNonEmptyAlgorithmException
// import pdi.jwt.exceptions.JwtExpirationException
// import pdi.jwt.exceptions.JwtNotBeforeException
// import pdi.jwt.exceptions.JwtNonSupportedAlgorithm
// import pdi.jwt.exceptions.JwtNonSupportedCurve
// import pdi.jwt.exceptions.JwtNonStringException
// import pdi.jwt.exceptions.JwtNonStringSetOrStringException
// import pdi.jwt.exceptions.JwtNonNumberException

// object RequestLogger {

//   def apply[F[_]: Sync](
//     cookieReader: SsoJwtReader[F],
//   ): HttpRoutes[F] => HttpRoutes[F] = { routes =>
//     Kleisli { req: org.http4s.Request[F] =>
//       OptionT.liftF(logRequest(req, cookieReader)) *> routes.run(req)
//     }
//   }

//   private def cookieMessage[F[_]: Monad](req: Request[F], cookieReader: SsoJwtReader[F]): F[String] =
//     cookieReader.attemptFindUser(req).map {
//       case None => "none"
//       case Some(Right(u)) => u.toString // TODO
//       case Some(Left(e)) =>
//         e match {
//           case _: JwtLengthException               => "JwtLengthException"
//           case _: JwtValidationException           => "JwtValidationException"
//           case _: JwtSignatureFormatException      => "JwtSignatureFormatException"
//           case _: JwtEmptySignatureException       => "JwtEmptySignatureException"
//           case _: JwtNonEmptySignatureException    => "JwtNonEmptySignatureException"
//           case _: JwtEmptyAlgorithmException       => "JwtEmptyAlgorithmException"
//           case _: JwtNonEmptyAlgorithmException    => "JwtNonEmptyAlgorithmException"
//           case _: JwtExpirationException           => "JwtExpirationException"
//           case _: JwtNotBeforeException            => "JwtNotBeforeException"
//           case _: JwtNonSupportedAlgorithm         => "JwtNonSupportedAlgorithm"
//           case _: JwtNonSupportedCurve             => "JwtNonSupportedCurve"
//           case _: JwtNonStringException            => "JwtNonStringException"
//           case _: JwtNonStringSetOrStringException => "JwtNonStringSetOrStringException"
//           case _: JwtNonNumberException            => "JwtNonNumberException"
//         }
//     }

//   private def logRequest[F[_]: Sync](req: Request[F], cookieReader: SsoJwtReader[F]): F[Unit] =
//     cookieMessage(req, cookieReader).flatMap { msg =>
//       Sync[F].delay(println("==> JWT: " + msg))
//     }

// }