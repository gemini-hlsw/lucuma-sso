// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect.*
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.model.OrcidId
import lucuma.core.model.ServiceUser
import lucuma.core.model.StandardUser
import lucuma.core.model.User
import lucuma.refined.*
import lucuma.sso.client.SsoGraphQlClient
import lucuma.sso.service.orcid.OrcidIdGenerator
import lucuma.sso.service.simulator.SsoSimulator
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object SsoGraphQlClientSuite extends SsoSuite with Fixture with OrcidIdGenerator[IO]:

  given Logger[IO] =
    Slf4jLogger.getLoggerFromClass(getClass)

  def createClient(
    index:  PosLong,
    sso:    Client[IO],
    writer: SsoJwtWriter[IO]
  ): IO[SsoGraphQlClient[IO]] =
    for
      jwt    <- writer.newJwt(ServiceUser(User.Id(index), "bogus"))
      client <- SsoGraphQlClient.create(
        uri        = uri"https://sso.gpp.lucuma.xyz" / "graphql",
        client     = sso,
        serviceJwt = jwt
      )
    yield client

  def extractOrcidId(u: User): Option[OrcidId] =
    u match
      case StandardUser(_, _, _, p) => p.orcidId.some
      case _                        => none

  test("canonicalizePreAuthUser"):
    SsoSimulator[IO].use:
      case (_, _, sso, _, writer) =>
        for
          client  <- createClient(1L.refined, sso, writer)
          orcidId <- randomOrcidId
          user    <- client.canonicalizePreAuthUser(orcidId)
        yield expect(extractOrcidId(user) === orcidId.some)

  test("canonicalizePreAuthUser existing user"):
    SsoSimulator[IO].use:
      case (_, _, sso, _, writer) =>
        for
          client  <- createClient(1L.refined, sso, writer)
          orcidId <- randomOrcidId
          user1   <- client.canonicalizePreAuthUser(orcidId)
          user2   <- client.canonicalizePreAuthUser(orcidId)
        yield expect(user1.id === user2.id)