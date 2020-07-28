package gpp.sso.service.simulator

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import gpp.sso.model.GuestUser
import gpp.sso.service.database.Database

object DatabaseSimulator {

  def pool[F[_]: Sync]: F[Resource[F, Database[F]]] =
    Ref[F].of(1).map { userId =>
      new Database[F] {
        def createGuestUser: F[GuestUser] =
          userId.modify(n => (n + 1, GuestUser(GuestUser.Id(n))))
      } .pure[Resource[F, ?]]
    }

 }

