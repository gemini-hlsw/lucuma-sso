package gpp.sso.service.util

import java.io.ByteArrayInputStream
import java.security.PrivateKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.operator.jcajce._
import org.bouncycastle.openpgp._
import scala.util.control.NonFatal

/**
 * Methods to turn GPG ASCII-amored exported text into a JCA `PrivateKey`.
 */
object GpgPrivateKeyReader {

  // The demo code hints that this is threadsafe and can be shared.
  private val provider = new BouncyCastleProvider

  /** Turn the output from `gpg --armor --export-secret-keys <key>` into a JCA PrivateKey. */
  def privateKey(pgpArmorText: String, passphrase: String): Either[Throwable, PrivateKey] =
    try {
      val is = PGPUtil.getDecoderStream(new ByteArrayInputStream(pgpArmorText.getBytes("US-ASCII")))
      val kr = new PGPSecretKeyRingCollection(is, new JcaKeyFingerprintCalculator)
      val sk = kr.iterator.next.getSecretKey
      val kd = new JcePBESecretKeyDecryptorBuilder().setProvider(provider).build(passphrase.toCharArray)
      val pk = sk.extractPrivateKey(kd)
      val kc = new JcaPGPKeyConverter
      Right(kc.getPrivateKey(pk))
    } catch {
      case NonFatal(e) => Left(e)
    }

}