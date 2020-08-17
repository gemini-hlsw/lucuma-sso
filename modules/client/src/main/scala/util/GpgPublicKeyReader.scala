// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gpp.sso.client.util

import java.io.ByteArrayInputStream
import java.security.PublicKey
import org.bouncycastle.openpgp.operator.jcajce._
import org.bouncycastle.openpgp._
import scala.util.control.NonFatal

/** Methods to turn GPG ASCII-amored exported text into JCA `PublicKey`. */
object GpgPublicKeyReader {

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

}