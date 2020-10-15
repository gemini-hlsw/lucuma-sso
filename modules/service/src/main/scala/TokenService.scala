// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import pdi.jwt.JwtClaim
import java.util.UUID
import lucuma.core.model.StandardRole
import lucuma.core.model.User
import lucuma.core.model.GuestUser
import lucuma.sso.service.database.Database
import java.{util => ju}
import scala.annotation.unused
import cats.effect.Sync

/**
 * A service that issues and redeems refresh tokens, which represent Lucuma sessions. A refresh
 * token is associated with a specific user/role and can be redeemed for a JWT claim (which must
 * be done periodically because JWT claims expire quickly). Refresh tokens do not expire, but they
 * may be deleted if they are unused for a long time or if the associated user is disabled, or if
 * there is some reason we wish to force all users to re-authenticate.
 */
trait TokenService[F[_]] {

  /**
   * Issue a refresh token for a guest user. For standard users, use the overload that takes
   * a role. Tokens are not available for service users.
   */
  def issueToken(user: GuestUser): F[UUID]

  /**
   * Issue a refresh token for a standard user in the specified role. For guest users, use the
   * overload that takes a guest user. Tokens are not available for service users.
   */
  def issueToken(role: StandardRole.Id): F[UUID]

  /**
   * Given a refresh token, yield a new JWT claim (or `None` if the token is invalid). Refresh
   * tokens never expire, but claims expire quickly. This is the mechanism by which you get a
   * renewed claim.
   */
  def redeemToken(token: UUID): F[Option[JwtClaim]]

  /**
   * Delete an individual refresh token. Future attempts to redeem this token will fail. This
   * operation always succeeds, even if the provided token is invalid.
   */
  def deleteToken(token: UUID): F[Unit]

  /**
   * Delete all refresh tokens associated with the given user. This will force the user to log in
   * again on all devices when current JWTs expire.
   */
  def deleteTokens(id: User.Id): F[Unit]

  /**
   * Delete all refresh tokens for all users. This will force all users to log in again on all
   * devices when current JWTs expire.
   */
  def deleteAllTokens: F[Unit]

}

object TokenService {

  def apply[F[_]: Sync](
    @unused database:  Database[F],
    @unused jwtWriter: SsoJwtWriter[F]
  ): TokenService[F] =
    new TokenService[F] {

      def issueToken(user: GuestUser): F[ju.UUID] =
        Sync[F].delay(UUID.randomUUID())

      def issueToken(role: StandardRole.Id): F[ju.UUID] = ???

      def redeemToken(token: ju.UUID): F[Option[JwtClaim]] = ???

      def deleteToken(token: ju.UUID): F[Unit] = ???

      def deleteTokens(id: User.Id): F[Unit] = ???

      def deleteAllTokens: F[Unit] = ???

    }


}