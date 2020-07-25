package gpp.sso.service.simulator

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import gpp.sso.model.GuestUser
import gpp.sso.service.database.Database

object DatabaseSimulator {

  val userId: Ref[IO, Int] =
    Ref[IO].of(1).unsafeRunSync

  val database: Database[IO] =

    new Database[IO] {
      def createGuestUser: IO[GuestUser] =
        userId.modify(n => (n + 1, GuestUser(GuestUser.Id(n))))
    }

  def pool: Resource[IO, Database[IO]] =
    Resource.liftF(database.pure[IO])

}

