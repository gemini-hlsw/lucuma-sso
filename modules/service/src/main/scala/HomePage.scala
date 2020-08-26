// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service

import scala.xml.Elem
import lucuma.sso.model.User
import io.circe.syntax._
import pdi.jwt.exceptions.JwtException
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.exceptions.JwtValidationException
import lucuma.sso.model.GuestUser
import lucuma.sso.model.ServiceUser
import lucuma.sso.model.StandardUser
import java.net.URLEncoder
import org.http4s.Uri


object HomePage {

  val script: String =
    s"""|
        |function authAsGuest() {
        |  var req = new XMLHttpRequest();
        |  req.open('POST', '/api/v1/authAsGuest');
        |  req.onreadystatechange = function () {
        |    location.reload();
        |  };
        |  req.send();
        |}
        |function logout() {
        |  var req = new XMLHttpRequest();
        |  req.open('POST', '/api/v1/logout');
        |  req.onreadystatechange = function () {
        |    location.reload();
        |  };
        |  req.send();
        |}
        |
        |""".stripMargin

  def apply(myUri: Uri, ou: Option[Either[JwtException, User]]): Elem =
    <html>
      <script>{ script }</script>
      <h2>Lucuma SSO</h2>
      {
        ou match {
          case None =>
            <div>You are not logged in.</div>
            <ul>
              <li><a href={ s"/auth/stage1?state=${URLEncoder.encode(myUri.renderString, "UTF-8")}" }>Authenticate via ORCID.</a></li>
              <li><a href="javascript:authAsGuest()">Continue as guest.</a></li>
            </ul>

          case Some(Left(e)) =>
            <div>
              {
                e match {
                  case _: JwtExpirationException => <div>Your JWT has expired.</div>
                  case _: JwtValidationException => <div>Your JWT could not be validated.</div>
                  case e => <div>{e.toString}</div>
                }
              }
              <ul>
                <li><a href={ s"/auth/stage1?state=${URLEncoder.encode(myUri.renderString, "UTF-8")}" }>Authenticate via ORCID.</a></li>
                <li><a href="javascript:authAsGuest()">Continue as Guest</a></li>
              </ul>
            </div>

          case Some(Right(u)) =>
            <div>You are logged in.</div>
            <pre>{u.asJson.spaces2}</pre>
            <ul>
              {
                u match {
                  case GuestUser(_) =>
                    <li><a href={ s"/auth/stage1?state=${URLEncoder.encode(myUri.renderString, "UTF-8")}" }>Authenticate via ORCID.</a></li>
                  case ServiceUser(_, _) =>
                  case StandardUser(_, _, _, p) =>
                    <li>
                      Display name is
                      <a href={ s"https://orcid.org/${p.orcid.value}" }><img alt="ORCID logo" src="https://orcid.org/sites/default/files/images/orcid_16x16.png" width="16" height="16"/></a>
                      <b>{p.displayName}</b>.
                    </li>
                }
              }
              <li><a href="/api/v1/whoami">whoami</a></li>
              <li><a href="javascript:logout()">Log out.</a></li>
            </ul>


        }
      }
    </html>

}