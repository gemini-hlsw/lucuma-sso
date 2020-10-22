// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.core.model.arb

import lucuma.core.model._
import lucuma.core.model.arb.ArbOrcidProfile._
import lucuma.core.util.arb.ArbGid._
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

trait ArbUser {
  import ArbStandardRole._

  val GenGuestUser: Gen[GuestUser] =
    arbitrary[User.Id].map(GuestUser(_))

  val GenServiceUser: Gen[ServiceUser] =
    for {
      id <- arbitrary[User.Id]
      n  <- arbitrary[String]
    } yield ServiceUser(id, n)

  val GenStandardUser: Gen[StandardUser] =
    for {
      id <- arbitrary[User.Id]
      r  <- arbitrary[StandardRole]
      rs <- arbitrary[List[StandardRole]].map(_.distinct)
      p  <- arbitrary(ArbOrcidProfile) // y u no infer?
    } yield StandardUser(id, r, rs, p)


  implicit val ArbUser: Arbitrary[User] =
    Arbitrary {
      Gen.oneOf(GenGuestUser, GenServiceUser, GenStandardUser)
    }

  implicit val CogUser: Cogen[User] =
    null

}

object ArbUser extends ArbUser
