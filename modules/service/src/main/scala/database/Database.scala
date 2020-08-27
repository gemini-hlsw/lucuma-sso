// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.database

import cats.implicits._
import lucuma.sso.model._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import lucuma.sso.service.orcid.OrcidAccess
import lucuma.sso.service.orcid.OrcidPerson
import cats.data.OptionT
import skunk.data.Completion.Delete
import cats.effect.Sync

// Minimal operations to support the basic use cases … add more when we add the GraphQL interface
trait Database[F[_]] {

  def createGuestUser: F[GuestUser]

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
    promotion: Option[User.Id],
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

      def upsertOrPromoteUser(
        access:    OrcidAccess,
        person:    OrcidPerson,
        promotion: Option[User.Id],
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
        promotion: User.Id,
        role:      RoleRequest
      ): F[StandardUser] =
        s.prepare(PromoteGuest).use { pq =>
          for {
            userId <- pq.unique(promotion ~ access ~ person)
            _      <- addRole(userId, role, true)
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
            _      <- addRole(userId, role, true) // we know there will be no conflict
            user   <- readStandardUser(userId)
          } yield user
        }

      // will raise a constraint failure if it's not a standard user id
      def addRole(user: User.Id, role: RoleRequest, makeActive: Boolean): F[StandardRole.Id] =
        for {
          roleId <- s.prepare(InsertRole).use(_.unique(user ~ role))
          _      <- s.prepare(UpdateActiveRole).use(_.execute(roleId ~ user)).whenA(makeActive)
        } yield roleId

      def findStandardUser(
        id: User.Id
      ): F[Option[StandardUser]] =
        s.prepare(SelectStandardUser).use { pq =>
          pq.stream(id, 64).compile.toList flatMap {
            case Nil => none[StandardUser].pure[F]
            case all @ ((roleId ~ user ~ _) :: _) =>
              val roles = all.map(_._2)
              roles.find(_.id === roleId) match {
                case None => Sync[F].raiseError(new RuntimeException(s"Unpossible: active role $roleId was not found for user: $id"))
                case Some(activeRole) =>
                  (user.copy(role = activeRole, otherRoles = roles.filterNot(_.id === roleId))).some.pure[F]
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

  val InsertGuestUser: Query[Void, GuestUser] =
    sql"""
      INSERT INTO lucuma_user (user_type)
      VALUES ('guest')
      RETURNING user_id
    """.query(user_id.map(GuestUser(_)))

  val UpdateProfile: Query[OrcidAccess ~ OrcidPerson, User.Id] =
    sql"""
      UPDATE lucuma_user
      SET orcid_access_token     = $uuid,
       -- orcid_token_expiration -- TODO
          orcid_given_name       = ${varchar.opt},
          orcid_credit_name      = ${varchar.opt},
          orcid_family_name      = ${varchar.opt},
          orcid_email            = ${varchar.opt}
      WHERE orcid_id = $orcid
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

  val PromoteGuest: Query[User.Id ~ OrcidAccess ~ OrcidPerson, User.Id] =
    sql"""
      UPDATE lucuma_user
      SET user_type              = 'standard',
          orcid_id               = $orcid,
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

  val InsertStandardUser: Query[OrcidAccess ~ OrcidPerson, User.Id] =
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
        $orcid,
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
  val SelectStandardUser: Query[User.Id, StandardRole.Id ~ StandardUser ~ StandardRole] =
    sql"""
      SELECT u.user_id,
             u.role_id,
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
        (user_id ~ role_id ~ orcid ~ varchar.opt ~ varchar.opt ~ varchar.opt ~ varchar.opt).map {
          case id ~ roleId ~ orcid ~ givenName ~ creditName ~ familyName ~ email =>
          roleId ~
          StandardUser(
            id = id,
            role = null, // TODO
            otherRoles = Nil, // TODO
            profile = OrcidProfile(
              orcid = orcid,
              givenName = givenName,
              creditName = creditName,
              familyName = familyName,
              primaryEmail = email.getOrElse("<none>") // TODO
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

  val DeleteUser: Command[User.Id] =
    sql"""
      DELETE FROM lucuma_user WHERE user_id = $user_id
    """.command

  val InsertRole: Query[User.Id ~ RoleRequest, StandardRole.Id] =
    sql"""
      INSERT INTO lucuma_role (user_id, role_type, role_ngo)
      VALUES ($user_id, $role_type, ${partner.opt})
      RETURNING role_id
    """
      .query(role_id)
      .contramap { case id ~ rr => id ~ rr.tpe ~ rr.partnerOption }

  val UpdateActiveRole: Command[StandardRole.Id ~ User.Id] =
    sql"""
      UPDATE lucuma_user
      SET    role_id = $role_id
      WHERE  user_id = $user_id
    """.command

}