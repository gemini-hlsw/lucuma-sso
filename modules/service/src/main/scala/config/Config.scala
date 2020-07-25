package gpp.sso.service.config

import cats.effect._
import cats.implicits._
import ciris._
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyPairGenerator
import java.security.SecureRandom

final case class Config(
  environment: Environment,
  database:    DatabaseConfig,
  orcid:       OrcidConfig,
  publicKey:   PublicKey,
  privateKey:  PrivateKey,
  httpPort:    Int,
  // tracing: jaeger, honeycomb, log, no-op
)

object Config {

  val Local: Config = {

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
      OrcidConfig.Local,
      keyPair.getPublic,
      keyPair.getPrivate,
      8080
    )

  }

  def config: ConfigValue[Config] =
    env("GPP_SSO_ENVIRONMENT").as[Environment].default(Environment.Local).flatMap {

      case Environment.Local =>
        ConfigValue.default(Local)

      case envi => (
        env("PORT").as[Int],
        DatabaseConfig.config,
        OrcidConfig.config,
        env("GPG_SSO_PUBLIC_KEY").as[PublicKey],
        env("GPG_SSO_PRIVATE_KEY"),
        env("GPG_SSO_PASSPHRASE").as[String],
      ).parTupled.flatMap { case (port, dbc, orc, pkey, text, pass) =>
        for {
          skey <- default(text).as[PrivateKey](privateKey(pass))
        } yield Config(envi, dbc, orc, pkey, skey, port)
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