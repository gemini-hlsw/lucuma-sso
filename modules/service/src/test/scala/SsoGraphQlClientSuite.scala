// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import cats.effect.*
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.model.ServiceUser
import lucuma.core.model.User
import lucuma.core.model.UserProfile
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

  test("canonicalizePreAuthUser"):
    val fallback = UserProfile("Gavrilo".some, "Princip".some, none, "gprincip@mladabosnia.org".some)

    SsoSimulator[IO].use:
      case (_, _, sso, _, writer) =>
        for
          client  <- createClient(1L.refined, sso, writer)
          orcidId <- randomOrcidId
          user    <- client.canonicalizePreAuthUser(orcidId, fallback)
        yield expect(user.displayName === "Gavrilo Princip")

  test("canonicalizePreAuthUser existing user"):
    val fallback1 = UserProfile("Gavrilo".some, "Princip".some, none, "gprincip@mladabosnia.org".some)
    val fallback2 = UserProfile("Charles".some, "Guiteau".some, none, "cguiteau@oneida.org".some)

    SsoSimulator[IO].use:
      case (_, _, sso, _, writer) =>
        for
          client  <- createClient(1L.refined, sso, writer)
          orcidId <- randomOrcidId
          user1   <- client.canonicalizePreAuthUser(orcidId, fallback1)
          user2   <- client.canonicalizePreAuthUser(orcidId, fallback2)
        yield expect.all(
          user1.id === user2.id,
          user2.displayName === "Charles Guiteau"
        )

  test("updateFallback"):
    val fallback1 = UserProfile("Charles".some, "Guiteau".some, none, "cguiteau@oneida.org".some)
    val fallback2 = UserProfile("Leon".some, "Czolgosz".some, none, "lczolgosz@silaclub.org".some)

    SsoSimulator[IO].use:
      case (_, _, sso, _, writer) =>
        for
          client  <- createClient(1L.refined, sso, writer)
          orcidId <- randomOrcidId
          _       <- client.canonicalizePreAuthUser(orcidId, fallback1)
          user    <- client.updateFallback(orcidId, fallback2)
        yield expect(user.get.displayName === "Leon Czolgosz")

  test("updateFallback unknown orcid"):
    val fallback = UserProfile("Charles".some, "Guiteau".some, none, "cguiteau@oneida.org".some)

    SsoSimulator[IO].use:
      case (_, _, sso, _, writer) =>
        for
          client  <- createClient(1L.refined, sso, writer)
          orcidId <- randomOrcidId
          user    <- client.updateFallback(orcidId, fallback)
        yield expect(user.isEmpty)