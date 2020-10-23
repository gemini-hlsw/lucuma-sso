// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.example

import org.http4s.Uri
import org.http4s.Uri.Host
import org.http4s.Uri.Scheme
import org.http4s.Uri.RegName
import org.http4s.implicits._
import org.http4s.Uri.Authority

case class Config(
  host:    Host,
  port:    Int,
  scheme:  Scheme,
  ssoRoot: Uri,
) {

  def uri: Uri =
    Uri(
      scheme    = Some(scheme),
      authority = Some(Authority(host = host, port = Some(port)))
    )

}

object Config {

  val Local: Config =
    Config(
      host = RegName("local.lucuma.xyz"),
      port = 8082,
      scheme = Scheme.http,
      ssoRoot = uri"http://local.lucuma.xyz:8080"
    )

}

