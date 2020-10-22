package lucuma.sso.client

import cats.kernel.Eq
import io.circe.testing.CodecTests
import io.circe.testing.instances.arbitraryJson
import lucuma.core.model._
import lucuma.core.model.arb.ArbOrcidProfile._
import lucuma.core.model.arb.ArbStandardRole._
import lucuma.core.model.arb.ArbUser._
import lucuma.core.util.arb.ArbGid._
import lucuma.sso.client.codec.orcidProfile._
import lucuma.sso.client.codec.role._
import lucuma.sso.client.codec.user._
import munit.DisciplineSuite
import munit.ScalaCheckSuite

class CodecSuite extends ScalaCheckSuite with DisciplineSuite {

  // TODO: move to core
  implicit val EqStandardRole: Eq[StandardRole] =
    Eq.fromUniversalEquals

  // TODO: move to core
  implicit val EqUser: Eq[User] =
    Eq.fromUniversalEquals

  checkAll("GidCodec", CodecTests[User.Id].unserializableCodec)
  checkAll("OrcidProfileCodec", CodecTests[OrcidProfile].unserializableCodec)
  checkAll("StandardRoleCodec", CodecTests[StandardRole].unserializableCodec)
  checkAll("UserCodec", CodecTests[User].unserializableCodec)

}


