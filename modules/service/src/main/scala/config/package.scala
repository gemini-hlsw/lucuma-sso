package gpp.sso.service

import cats.implicits._
import ciris.ConfigDecoder
import gpp.sso.client.util.GpgKeyReader
import java.net.URI
import java.security.{ PrivateKey, PublicKey }

package object config {

  def privateKey(passphrase: String): ConfigDecoder[String, PrivateKey] =
    ConfigDecoder[String].mapOption("PrivateKey") { s =>
      GpgKeyReader.privateKey(s, passphrase).toOption
    }

  implicit val publicKey: ConfigDecoder[String, PublicKey] =
    ConfigDecoder[String].mapOption("PublicKey") { s =>
      GpgKeyReader.publicKey(s).toOption
    }

  implicit val uri: ConfigDecoder[String, URI] =
    ConfigDecoder[String].map(new URI(_))

}
