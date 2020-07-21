package gpp.sso.service.database

import cats._
import cats.implicits._
import gpp.sso.model._
import skunk._
import skunk.implicits._
import skunk.codec.all._

// Minimal operations to support the basic use cases … add more when we add the GraphQL interface
trait Database[F[_]] {

  // Create
  def createGuestUser: F[GuestUser]
  // def createStandardUser(profile: OrcidProfile, role: RoleRequest): F[StandardUser]

  // // Read
  // def readGuestUser(id: GuestUser.Id): F[GuestUser]
  // def readUser(orcid: Orcid): F[StandardUser]

  // // Promote
  // def promoteUser(id: GuestUser.Id, profile: OrcidProfile, role: RoleRequest): F[StandardUser]

}

// sealed trait RoleRequest
// object RoleRequest {
//   final case object Pi extends RoleRequest
//   final case class  Ngo(partner: Partner) extends RoleRequest
//   final case object Staff extends RoleRequest
//   final case object Admin extends RoleRequest
// }

object Database {

  def fromSession[F[_]: Functor](s: Session[F]): Database[F] =
    new Database[F] {

      def createGuestUser: F[GuestUser] =
        s.unique(
          sql"""
            INSERT INTO gpp_user (user_type)
            VALUES ('guest')
            RETURNING user_id
          """.query(int4)
        ).map(n => GuestUser(GuestUser.Id(n)))

    }

}