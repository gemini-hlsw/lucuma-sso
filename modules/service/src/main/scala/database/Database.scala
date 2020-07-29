package gpp.sso.service.database

import cats.implicits._
import gpp.sso.model._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import cats.effect.Bracket
import gpp.sso.service.orcid.OrcidAccess
import gpp.sso.service.orcid.OrcidPerson
import cats.data.OptionT
import skunk.data.Type

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
   *
   * Note that if `promotion` specifies a non-existent or non-guest user then the behavior will
   * be the same as if `None` were specified. In practice it would be difficult to do this
   * unintentionally.
   */
  def upsertOrPromoteUser(
    access:    OrcidAccess,
    person:    OrcidPerson,
    promotion: Option[GuestUser.Id],
    role:      RoleRequest
  ) : F[(StandardUser, Boolean)]

  // // Read
  // def readGuestUser(id: GuestUser.Id): F[GuestUser]
  // def readUser(orcid: Orcid): F[StandardUser]

  // // Promote
  // def promoteUser(id: GuestUser.Id, profile: OrcidProfile, role: RoleRequest): F[StandardUser]

}

sealed trait RoleRequest
object RoleRequest {
  final case object Pi extends RoleRequest
  final case class  Ngo(partner: Partner) extends RoleRequest
  final case object Staff extends RoleRequest
  final case object Admin extends RoleRequest
}

object Database {

  def fromSession[F[_]: Bracket[*[_], Throwable]](s: Session[F]): Database[F] =
    new Database[F] {

      def createGuestUser: F[GuestUser] =
        s.unique(CreateGuestUser)

      def upsertOrPromoteUser(
        access:    OrcidAccess,
        person:    OrcidPerson,
        promotion: Option[GuestUser.Id],
        role:      RoleRequest
      ): F[(StandardUser, Boolean)] =
        s.transaction.use { _ =>
          OptionT(updateProfile(access, person)).tupleRight(true).getOrElseF {
            promotion match {
              case Some(guestId) => promoteGuest(access, person, guestId, role).tupleRight(false)
              case None          => createStandardUser(access, person /*, role */).tupleRight(false)
            }
          }
        }

      /** Update the specified ORCID profile and yield the associated `StandardUser`, if any. */
      def updateProfile(access: OrcidAccess, person: OrcidPerson): F[Option[StandardUser]] =
        s.prepare(UpdateProfile).use { pq =>
          OptionT(pq.option(access ~ person))
            .semiflatMap(readStandardUser)
            .value
        }

      def promoteGuest(
        access:    OrcidAccess,
        person:    OrcidPerson,
        promotion: GuestUser.Id,
        role:      RoleRequest
      ): F[StandardUser] = ???

      def createStandardUser(
        access:    OrcidAccess,
        person:    OrcidPerson,
        // role:      RoleRequest
      ): F[StandardUser] =
        s.prepare(CreateStandardUser).use { pq =>
          pq.unique(access ~ person).flatMap(readStandardUser)
        }

      def readStandardUser(
        id: StandardUser.Id
      ): F[StandardUser] =
        s.prepare(ReadStandardUser).use { pq =>
          pq.unique(id)
        }

  }

  // Some codecs we will use locally
  val orcid: Codec[Orcid] = Codec.simple[Orcid](_.value, Orcid.fromString(_).toRight("Invalid ORCID iD"), Type.varchar)
  val standard_user_id: Codec[StandardUser.Id] = int4.gimap
  val guest_user_id: Codec[GuestUser.Id] = int4.gimap
  val guest_user: Codec[GuestUser] = guest_user_id.gimap

  val CreateGuestUser: Query[Void, GuestUser] =
    sql"""
      INSERT INTO gpp_user (user_type)
      VALUES ('guest')
      RETURNING user_id
    """.query(guest_user)

  val UpdateProfile: Query[OrcidAccess ~ OrcidPerson, StandardUser.Id] =
    sql"""
      UPDATE gpp_user
      SET orcid_access_token     = $varchar,
       -- orcid_token_expiration -- TODO
          orcid_given_name       = ${varchar.opt},
          orcid_credit_name      = ${varchar.opt},
          orcid_family_name      = ${varchar.opt},
          orcid_email            = ${varchar.opt}
      WHERE orcid_id = $orcid
      RETURNING (user_id)
    """
      .query(standard_user_id)
      .contramap[OrcidAccess ~ OrcidPerson] {
        case access ~ person =>
          access.accessToken.toString      ~ // TODO
          person.name.givenName            ~
          person.name.creditName           ~
          person.name.familyName           ~
          person.primaryEmail.map(_.email) ~
          access.orcidId
      }


  val CreateStandardUser: Query[OrcidAccess ~ OrcidPerson, StandardUser.Id] =
    sql"""
      INSERT INTO gpp_user (
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
        $varchar,
        ${varchar.opt},
        ${varchar.opt},
        ${varchar.opt},
        ${varchar.opt}
      )
      RETURNING (user_id)
    """
      .query(standard_user_id)
      .contramap[OrcidAccess ~ OrcidPerson] {
        case access ~ person =>
          access.orcidId                   ~
          access.accessToken.toString      ~ // TODO
          person.name.givenName            ~
          person.name.creditName           ~
          person.name.familyName           ~
          person.primaryEmail.map(_.email)
      }

  val ReadStandardUser =
    sql"""
      SELECT user_id, orcid_id, orcid_given_name, orcid_credit_name, orcid_family_name, orcid_email
      FROM   gpp_user
      WHERE  user_id   = $standard_user_id
      AND    user_type = 'standard' -- sanity check
    """
      .query(standard_user_id ~ orcid ~ varchar.opt ~ varchar.opt ~ varchar.opt ~ varchar.opt)
      .map { case id ~ orcid ~ givenName ~ creditName ~ familyName ~ email =>
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
      }


 }