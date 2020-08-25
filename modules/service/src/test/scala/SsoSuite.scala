package gpp.sso.service

import cats.effect._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import weaver._

trait SsoSuite extends SimpleIOSuite {

  implicit val logger: Logger[IO] =
    Slf4jLogger.getLogger

}