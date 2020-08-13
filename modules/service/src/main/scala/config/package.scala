package gpp.sso.service

import cats.implicits._
import ciris.ConfigDecoder
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

}
