// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.config

import cats.effect._
import cats.implicits._
import ciris._
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyPairGenerator
import java.security.SecureRandom
import lucuma.sso.client.SsoCookieReader
import lucuma.sso.service.SsoCookieWriter
import lucuma.sso.client.util.JwtDecoder
import lucuma.sso.service.util.JwtEncoder
import cats.MonadError
import scala.concurrent.duration._
import org.http4s.Uri
import org.http4s.Uri.RegName

final case class Config(
  environment:  Environment,
  database:     DatabaseConfig,
  orcid:        OrcidConfig,
  publicKey:    PublicKey,
  privateKey:   PrivateKey,
  httpPort:     Int,
  cookieDomain: Option[String],
  scheme:       Uri.Scheme,
  hostname:     String,
) {

  val authority: Uri.Authority =
    Uri.Authority(
      host = RegName(hostname),
      port = Some(httpPort)
    )

  // TODO: parameterize
  val JwtLifetime    = 10.minutes

  def cookieReader[F[_]: MonadError[?[_], Throwable]] =
    SsoCookieReader(JwtDecoder.withPublicKey[F](publicKey))

  def cookieWriter[F[_]: Sync] =
    SsoCookieWriter(JwtEncoder.withPrivateKey[F](privateKey), JwtLifetime, cookieDomain)

}

object Config {

  def local(orcid: OrcidConfig): Config = {

    // Generate a random key pair. This basically means nobody is going to be able to validate keys
    // issued here because they have no way to get the public key. It may end up being better to use
    // a constant well-known key but of course if that makes it into production all is lost. So we
    // will err on the side of safety for the moment.
    val keyGen  = KeyPairGenerator.getInstance("RSA", "SunRsaSign")
    val random  = SecureRandom.getInstance("SHA1PRNG", "SUN")
    val keyPair = { keyGen.initialize(1024, random); keyGen.generateKeyPair }

    // And our canned config for local development.
    Config(
      Environment.Local,
      DatabaseConfig.Local,
      orcid,
      keyPair.getPublic,
      keyPair.getPrivate,
      8080,
      None,
      Uri.Scheme.http,
      "localhost",
    )

  }

  def config: ConfigValue[Config] =
    envOrProp("LUCUMA_SSO_ENVIRONMENT").as[Environment].default(Environment.Local).flatMap {

      case Environment.Local =>
        OrcidConfig.config.map(local)

      case envi => (
        envOrProp("PORT").as[Int],
        DatabaseConfig.config,
        OrcidConfig.config,
        envOrProp("GPG_SSO_PUBLIC_KEY").as[PublicKey],
        envOrProp("GPG_SSO_PRIVATE_KEY").redacted,
        envOrProp("GPG_SSO_PASSPHRASE").redacted,
        envOrProp("LUCUMA_SSO_COOKIE_DOMAIN").option,
        envOrProp("LUCUMA_SSO_HOSTNAME"),
      ).parTupled.flatMap { case (port, dbc, orc, pkey, text, pass, domain, host) =>
        for {
          skey <- default(text).as[PrivateKey](privateKey(pass))
        } yield Config(envi, dbc, orc, pkey, skey, port, domain, Uri.Scheme.https, host)
      }

    }

}

object ConfigTest extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      cfg <- Config.config.load[IO]
      _   <- IO(println(cfg))
    } yield ExitCode.Success

}