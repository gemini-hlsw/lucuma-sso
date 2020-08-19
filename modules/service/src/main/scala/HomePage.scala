package gpp.sso.service

import scala.xml.Elem
import gpp.sso.model.User
import io.circe.syntax._
import pdi.jwt.exceptions.JwtException
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.exceptions.JwtValidationException
import gpp.sso.model.GuestUser
import gpp.sso.model.ServiceUser
import gpp.sso.model.StandardUser
import java.net.URLEncoder


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

  def apply(ou: Option[Either[JwtException, User]]): Elem =
    <html>
      <script>{ script }</script>
      <h2>GPP-SSO</h2>
      {
        ou match {
          case None =>
            <div>You are not logged in.</div>
            <ul>
              <li><a href={ s"/auth/stage1?state=${URLEncoder.encode("http://localhost:8080/", "UTF-8")}" }>Authenticate via ORCID.</a></li>
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
                <li><a href={ s"/auth/stage1?state=${URLEncoder.encode("http://localhost:8080/", "UTF-8")}" }>Authenticate via ORCID.</a></li>
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
                    <li>Authenticate via ORCID.</li>
                  case ServiceUser(_, _) =>
                  case StandardUser(_, _, _, p) =>
                    <li>
                      Display name is
                      <a href={ s"https://orcid.org/${p.orcid.value}" }><img alt="ORCID logo" src="https://orcid.org/sites/default/files/images/orcid_16x16.png" width="16" height="16"/></a>
                      <b>{p.displayName}</b>.
                    </li>
                }
              }
              <li><a href="javascript:logout()">Log out.</a></li>
            </ul>


        }
      }
    </html>

}