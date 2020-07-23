package gpp.sso.client.util

import java.io.ByteArrayInputStream
import java.security.PrivateKey
import java.security.PublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.operator.jcajce._
import org.bouncycastle.openpgp._
import scala.util.control.NonFatal

/** Methods to turn GPG ASCII-amored exported text into JCA keys. */
object GpgKeyReader {

  // The demo code hints that this is threadsafe and can be shared.
  private val provider = new BouncyCastleProvider

  /** Turn the output from `gpg --armor --export <key>` into a JCA PublicKey. */
  def publicKey(pgpArmorText: String): Either[Throwable, PublicKey] =
    try {
      val is = PGPUtil.getDecoderStream(new ByteArrayInputStream(pgpArmorText.getBytes("US-ASCII")))
      val kr = new PGPPublicKeyRingCollection(is, new JcaKeyFingerprintCalculator)
      val pk = kr.iterator.next.getPublicKey
      val kc = new JcaPGPKeyConverter
      Right(kc.getPublicKey(pk))
    } catch {
      case NonFatal(e) => Left(e)
    }

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