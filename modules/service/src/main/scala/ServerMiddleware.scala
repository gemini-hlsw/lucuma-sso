// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import natchez.http4s.implicits._
import natchez.Trace
import cats._
import org.http4s.HttpRoutes
import lucuma.sso.client.RequestLogger
import lucuma.sso.client.SsoCookieReader
import cats.effect._
import lucuma.sso.service.config.Environment
import lucuma.sso.service.config.Environment._
import org.http4s.server.middleware.ErrorAction
import io.chrisdavenport.log4cats.Logger

/** A module of all the middlewares we apply to the server routes. */
object ServerMiddleware {

  type Middleware[F[_]] = Endo[HttpRoutes[F]]

  /** A middleware that adds distributed tracing. */
  def natchez[F[_]: Bracket[*[_], Throwable]: Trace]: Middleware[F] =
    natchezMiddleware[F]

  /** A middleware that logs the user making the request (if any). */
  def userLogging[F[_]: Sync](
    cookieReader: SsoCookieReader[F],
  ): Middleware[F] =
    RequestLogger(cookieReader)

  /** A middleware that logs request and response. Headers are redacted in staging/production. */
  def logging[F[_]: Concurrent: ContextShift](
    env:          Environment,
  ): Middleware[F] =
    org.http4s.server.middleware.Logger.httpRoutes[F](
      logHeaders        = true,
      logBody           = false,
      redactHeadersWhen = { _ =>
        env match {
          case Local                => false
          case Staging | Production => false // TODO: Headers.SensitiveHeaders.contains(h)
        }
      }
    )

  /** A middleware that reports errors during requets processing. */
  def errorReporting[F[_]: MonadError[*[_], Throwable]: Logger]: Middleware[F] = routes =>
    ErrorAction.httpRoutes.log(
      httpRoutes              = routes,
      messageFailureLogAction = Logger[F].error(_)(_),
      serviceErrorLogAction   = Logger[F].error(_)(_)
    )

  /** A middleware that composes all the others defined in this module. */
  def apply[F[_]: Concurrent: ContextShift: Trace: Logger](
    env:          Environment,
    cookieReader: SsoCookieReader[F],
  ): Middleware[F] =
    natchez                   andThen
    userLogging(cookieReader) andThen
    logging(env)              andThen
    errorReporting

}