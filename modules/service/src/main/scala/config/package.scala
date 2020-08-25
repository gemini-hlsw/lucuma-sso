// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.service

import cats.implicits._
import ciris._
import java.net.URI
import java.security.{ PrivateKey, PublicKey }
import gpp.sso.service.util.GpgPrivateKeyReader
import gpp.sso.client.util.GpgPublicKeyReader

package object config {

  def privateKey(passphrase: String): ConfigDecoder[String, PrivateKey] =
    ConfigDecoder[String].mapOption("PrivateKey") { s =>
      GpgPrivateKeyReader.privateKey(s, passphrase).toOption
    }

  implicit val publicKey: ConfigDecoder[String, PublicKey] =
    ConfigDecoder[String].mapOption("PublicKey") { s =>
      GpgPublicKeyReader.publicKey(s).toOption
    }

  implicit val uri: ConfigDecoder[String, URI] =
    ConfigDecoder[String].map(new URI(_))

  /**
   * Ciris config value that is read from the environment, falling back to Java system properties
   * if the environment variable is not set.
   */
  def envOrProp(name: String): ConfigValue[String] =
    env(name) or prop(name)

}
