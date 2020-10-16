// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.database

import cats.implicits._
import lucuma.core.model._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import lucuma.sso.service.SessionToken
import lucuma.sso.service.orcid.OrcidAccess
import lucuma.sso.service.orcid.OrcidPerson
import cats.data.OptionT
import skunk.data.Completion.Delete
import cats.effect.Sync

// Minimal operations to support the basic use cases … add more when we add the GraphQL interface
trait Database[F[_]] {

  def createGuestUser: F[GuestUser]
  def createGuestUserRefreshToken(guestUser: GuestUser): F[SessionToken]
  def createGuestUserAndRefreshToken: F[(GuestUser, SessionToken)]

  def createStandardUserRefreshToken(roleId: StandardRole.Id): F[SessionToken]

  def findGuestUserFromToken(token: SessionToken): F[Option[GuestUser]]

  def getGuestUserFromToken(token: SessionToken): F[GuestUser]

  /**
   * [After ORCID authenticaton] insert, update, or promote the specified ORCID user, yielding a
   * `StandardUser` and a flag indicating whether objects owned by the specified guest user (if
   * any) should be chowned to the new `StandardUser`. This will be true only if a guest user has
   * authenticated as an existing user (if the guest user authenticates as a new user then the
   * guest user will be promoted without a change in id).
   *
   * If a chown is needed then it's the caller's responsibility to do so, after which the guest
   * user should be deleted.
   *
   * Note that `role` is used only when creating a new user or promoting a guest user. It is
   * ignored for existing users.
   */
  def upsertOrPromoteUser(
    access:    OrcidAccess,
    person:    OrcidPerson,
    promotion: Option[GuestUser],
    role:      RoleRequest
  ) : F[(StandardUser, Boolean)]

  def deleteUser(id: User.Id): F[Boolean]
  // // Read
  // def readGuestUser(id: User.Id): F[GuestUser]
  // def readUser(orcid: Orcid): F[StandardUser]

  // // Promote
  // def promoteUser(id: User.Id, profile: OrcidProfile, role: RoleRequest): F[StandardUser]

}

object Database extends Codecs {

  def fromSession[F[_]: Sync](s: Session[F]): Database[F] =
    new Database[F] {

      def createGuestUser: F[GuestUser] =
        s.unique(InsertGuestUser)

      def createGuestUserRefreshToken(guestUser: GuestUser): F[SessionToken] =
        s.prepare(InsertGuestUserSessionToken).use(_.unique(guestUser.id))

      def createGuestUserAndRefreshToken: F[(GuestUser, SessionToken)] =
        s.transaction.use { _ =>
          createGuestUser.mproduct(createGuestUserRefreshToken) // 🔥
        }

      def createStandardUserRefreshToken(roleId: StandardRole.Id): F[SessionToken] =
        s.prepare(InsertStandardUserSessionToken).use(_.unique(roleId))

      def findGuestUserFromToken(token: SessionToken): F[Option[GuestUser]] =
        s.prepare(SelectGuestUserForSessionToken).use(_.option(token))

      def getGuestUserFromToken(token: SessionToken): F[GuestUser] =
        findGuestUserFromToken(token)
          .flatMap(_.toRight(new RuntimeException(s"No guest user for session token: ${token.value}")).liftTo[F])

      def upsertOrPromoteUser(
        access:    OrcidAccess,
        person:    OrcidPerson,
        promotion: Option[GuestUser],
        role:      RoleRequest
      ): F[(StandardUser, Boolean)] =
        s.transaction.use { _ =>
          // Try to update the profile and read the user. If it works then the user already exists
          // and we need to chown the guest user's stuff (if any). Otherwise, if there is a guest
          // user then we need to promote it, if not it's a brand new user.
          OptionT(updateProfile(access, person)).tupleRight(true).getOrElseF {
            promotion match {
              case Some(guestId) => promoteGuest(access, person, guestId, role).tupleRight(false)
              case None          => createStandardUser(access, person, role).tupleRight(false)
            }
          }
        }

      def deleteUser(id: User.Id): F[Boolean] =
        s.prepare(DeleteUser).use { pq =>
          pq.execute(id).map {
            case Delete(c) => c > 0
            case _         => sys.error("unpossible")
          }
        }

      /// HELPERS

      // Update the specified ORCID profile and yield the associated `StandardUser`, if any.
      def updateProfile(access: OrcidAccess, person: OrcidPerson): F[Option[StandardUser]] =
        s.prepare(UpdateProfile).use { pq =>
          OptionT(pq.option(access ~ person))
            .semiflatMap(readStandardUser)
            .value
        }

      def promoteGuest(
        access:    OrcidAccess,
        person:    OrcidPerson,
        promotion: GuestUser,
        role:      RoleRequest
      ): F[StandardUser] =
        s.prepare(PromoteGuest).use { pq =>
          for {
            userId <- pq.unique(promotion.id ~ access ~ person)
            _      <- addRole(userId, role)
            user   <- readStandardUser(userId)
          } yield user
        }

      def createStandardUser(
        access:    OrcidAccess,
        person:    OrcidPerson,
        role:      RoleRequest
      ): F[StandardUser] =
        s.prepare(InsertStandardUser).use { pq =>
          for {
            userId <- pq.unique(access ~ person)
            _      <- addRole(userId, role)
            user   <- readStandardUser(userId)
          } yield user
        }

      // will raise a constraint failure if it's not a standard user id
      def addRole(user: User.Id, role: RoleRequest): F[StandardRole.Id] =
        s.prepare(InsertRole).use(_.unique(user ~ role))

      def findStandardUser(
        id: User.Id
      ): F[Option[StandardUser]] =
        s.prepare(SelectStandardUser).use { pq =>
          pq.stream(id, 64).compile.toList flatMap {
            case Nil => none[StandardUser].pure[F]
            case all @ ((user ~ _) :: _) =>
              s.prepare(SelectDefaultRole).use { pq =>
                pq.unique(user.id)
              } .flatMap { roleId =>
                val roles = all.map(_._2)
                roles.find(_.id === roleId) match {
                  case None => Sync[F].raiseError(new RuntimeException(s"Unpossible: active role $roleId was not found for user: $id"))
                  case Some(activeRole) =>
                    (user.copy(role = activeRole, otherRoles = roles.filterNot(_.id === roleId))).some.pure[F]
                }
              }
          }
        }

      def readStandardUser(
        id: User.Id
      ): F[StandardUser] =
        findStandardUser(id).flatMap {
          case None => Sync[F].raiseError(new RuntimeException(s"No such standard user: $id"))
          case Some(u) => u.pure[F]
        }

  }

  private val InsertGuestUser: Query[Void, GuestUser] =
    sql"""
      INSERT INTO lucuma_user (user_type)
      VALUES ('guest')
      RETURNING user_id
    """.query(user_id.map(GuestUser(_)))

  private val UpdateProfile: Query[OrcidAccess ~ OrcidPerson, User.Id] =
    sql"""
      UPDATE lucuma_user
      SET orcid_access_token     = $uuid,
       -- orcid_token_expiration -- TODO
          orcid_given_name       = ${varchar.opt},
          orcid_credit_name      = ${varchar.opt},
          orcid_family_name      = ${varchar.opt},
          orcid_email            = ${varchar.opt}
      WHERE orcid_id = $orcid_id
      RETURNING (user_id)
    """
      .query(user_id)
      .contramap[OrcidAccess ~ OrcidPerson] {
        case access ~ person =>
          access.accessToken               ~ // TODO: expiration
          person.name.givenName            ~
          person.name.creditName           ~
          person.name.familyName           ~
          person.primaryEmail.map(_.email) ~
          access.orcidId
      }

  private val PromoteGuest: Query[User.Id ~ OrcidAccess ~ OrcidPerson, User.Id] =
    sql"""
      UPDATE lucuma_user
      SET user_type              = 'standard',
          orcid_id               = $orcid_id,
          orcid_access_token     = $uuid,
       -- orcid_token_expiration -- TODO
          orcid_given_name       = ${varchar.opt},
          orcid_credit_name      = ${varchar.opt},
          orcid_family_name      = ${varchar.opt},
          orcid_email            = ${varchar.opt}
      WHERE user_id   = $user_id
      AND   user_type = 'guest' -- sanity check
      RETURNING (user_id)
    """
      .query(user_id)
      .contramap[User.Id ~ OrcidAccess ~ OrcidPerson] {
        case id ~ access ~ person =>
          access.orcidId                   ~
          access.accessToken               ~ // TODO: expiration
          person.name.givenName            ~
          person.name.creditName           ~
          person.name.familyName           ~
          person.primaryEmail.map(_.email) ~
          id
      }

  private val InsertStandardUser: Query[OrcidAccess ~ OrcidPerson, User.Id] =
    sql"""
      INSERT INTO lucuma_user (
        user_type,
        orcid_id,
        orcid_access_token,
     -- orcid_token_expiration, -- TODO
        orcid_given_name,
        orcid_credit_name,
        orcid_family_name,
        orcid_email
      )
      VALUES (
        'standard',
        $orcid_id,
        $uuid,
        ${varchar.opt},
        ${varchar.opt},
        ${varchar.opt},
        ${varchar.opt}
      )
      RETURNING (user_id)
    """
      .query(user_id)
      .contramap[OrcidAccess ~ OrcidPerson] {
        case access ~ person =>
          access.orcidId                   ~
          access.accessToken               ~ // TODO: epiration
          person.name.givenName            ~
          person.name.creditName           ~
          person.name.familyName           ~
          person.primaryEmail.map(_.email)
      }


  /**
   * Query that reads a single standard user as a list of triples. The first two values are
   * constant (active role id, user with null role and Nil otherRoles) and the last value is a unique
   * role, from which the active and other roles must be selected.
   */
  private val SelectStandardUser: Query[User.Id, StandardUser ~ StandardRole] =
    sql"""
      SELECT u.user_id,
             u.orcid_id,
             u.orcid_given_name,
             u.orcid_credit_name,
             u.orcid_family_name,
             u.orcid_email,
             r.role_id,
             r.role_type,
             r.role_ngo
      FROM   lucuma_user u
      JOIN   lucuma_role r
      ON     u.user_id = r.user_id
      WHERE  u.user_id = $user_id
      AND    u.user_type = 'standard' -- sanity check
    """
      .query(
        (user_id ~ orcid_id ~ varchar.opt ~ varchar.opt ~ varchar.opt ~ varchar.opt).map {
          case id ~ orcidId ~ givenName ~ creditName ~ familyName ~ email =>
          StandardUser(
            id         = id,
            role       = null, // TODO
            otherRoles = Nil, // TODO
            profile    = OrcidProfile(
              orcidId      = orcidId,
              givenName    = givenName,
              creditName   = creditName,
              familyName   = familyName,
              primaryEmail = email
            )
          )
        } ~
        (role_id ~ role_type ~ partner.opt).map {
          // we really need emap here
          case id ~ RoleType.Admin ~ None    => StandardRole.Admin(id)
          case id ~ RoleType.Staff ~ None    => StandardRole.Staff(id)
          case id ~ RoleType.Ngo   ~ Some(p) => StandardRole.Ngo(id, p)
          case id ~ RoleType.Pi    ~ None    => StandardRole.Pi(id)
          case hmm => sys.error(s"welp, we really need emap here, sorry. anyway, invalid: $hmm")
        }
      )

  private val DeleteUser: Command[User.Id] =
    sql"""
      DELETE FROM lucuma_user WHERE user_id = $user_id
    """.command

  private val InsertRole: Query[User.Id ~ RoleRequest, StandardRole.Id] =
    sql"""
      INSERT INTO lucuma_role (user_id, role_type, role_ngo)
      VALUES ($user_id, $role_type, ${partner.opt})
      RETURNING role_id
    """
      .query(role_id)
      .contramap { case id ~ rr => id ~ rr.tpe ~ rr.partnerOption }

  /** Select the id of the weakest role for a user (typically the PI role). */
  private val SelectDefaultRole: Query[User.Id, StandardRole.Id] =
    sql"""
      SELECT DISTINCT ON (user_id) role_id
      FROM lucuma_role
      WHERE user_id=$user_id
      ORDER BY user_id, role_type ASC
    """.query(role_id)

  /** Create a session token for a guest user. */
  private val InsertGuestUserSessionToken: Query[User.Id, SessionToken] =
    sql"""
      INSERT INTO lucuma_session (user_id, user_type, role_id)
      VALUES ($user_id, 'guest', null)
      RETURNING refresh_token
      """.query(session_token)

  /** Create a session token for a standard user [role]. */
  private val InsertStandardUserSessionToken: Query[StandardRole.Id, SessionToken] =
    sql"""
      INSERT INTO lucuma_session (user_id, user_type, role_id)
      SELECT user_id, user_type, role_id
      FROM lucuma_role
      WHERE role_id = $role_id
      AND user_type = 'standard' -- sanity check
      RETURNING refresh_token
      """.query(session_token)

  private val SelectGuestUserForSessionToken: Query[SessionToken, GuestUser] =
    sql"""
      SELECT user_id
      FROM lucuma_session
      WHERE user_type = 'guest'
      AND   refresh_token = $session_token
    """.query(user_id).map(GuestUser(_))

}