package gpp.sso.service.orcid

import cats.effect.Sync
import org.http4s.EntityDecoder
import org.http4s.scalaxml._
import scala.xml.Elem

case class PersonInfo(
  givenName:    Option[String],
  familyName:   Option[String],
  creditName:   Option[String],
  primaryEmail: Option[String],
)

object PersonInfo {

  //   <person:person path="/0000-0003-1301-6629/person" xmlns:internal="http://www.orcid.org/ns/internal" xmlns:education="http://www.orcid.org/ns/education" xmlns:distinction="http://www.orcid.org/ns/distinction" xmlns:deprecated="http://www.orcid.org/ns/deprecated" xmlns:other-name="http://www.orcid.org/ns/other-name" xmlns:membership="http://www.orcid.org/ns/membership" xmlns:error="http://www.orcid.org/ns/error" xmlns:common="http://www.orcid.org/ns/common" xmlns:record="http://www.orcid.org/ns/record" xmlns:personal-details="http://www.orcid.org/ns/personal-details" xmlns:keyword="http://www.orcid.org/ns/keyword" xmlns:email="http://www.orcid.org/ns/email" xmlns:external-identifier="http://www.orcid.org/ns/external-identifier" xmlns:funding="http://www.orcid.org/ns/funding" xmlns:preferences="http://www.orcid.org/ns/preferences" xmlns:address="http://www.orcid.org/ns/address" xmlns:invited-position="http://www.orcid.org/ns/invited-position" xmlns:work="http://www.orcid.org/ns/work" xmlns:history="http://www.orcid.org/ns/history" xmlns:employment="http://www.orcid.org/ns/employment" xmlns:qualification="http://www.orcid.org/ns/qualification" xmlns:service="http://www.orcid.org/ns/service" xmlns:person="http://www.orcid.org/ns/person" xmlns:activities="http://www.orcid.org/ns/activities" xmlns:researcher-url="http://www.orcid.org/ns/researcher-url" xmlns:peer-review="http://www.orcid.org/ns/peer-review" xmlns:bulk="http://www.orcid.org/ns/bulk" xmlns:research-resource="http://www.orcid.org/ns/research-resource">
  //     <common:last-modified-date>2020-07-14T18:58:41.444Z</common:last-modified-date>
  //     <person:name visibility="public" path="0000-0003-1301-6629">
  //         <common:created-date>2019-05-13T17:46:18.919Z</common:created-date>
  //         <common:last-modified-date>2020-07-14T15:55:35.444Z</common:last-modified-date>
  //         <personal-details:given-names>Rob</personal-details:given-names>
  //         <personal-details:family-name>Norris</personal-details:family-name>
  //         <personal-details:credit-name>Tangley Polecat</personal-details:credit-name>
  //     </person:name>
  //     <other-name:other-names path="/0000-0003-1301-6629/other-names"/>
  //     <researcher-url:researcher-urls path="/0000-0003-1301-6629/researcher-urls"/>
  //     <email:emails path="/0000-0003-1301-6629/email">
  //         <common:last-modified-date>2020-07-14T18:58:41.444Z</common:last-modified-date>
  //         <email:email visibility="public" verified="true" primary="true">
  //             <common:created-date>2019-05-13T17:46:19.157Z</common:created-date>
  //             <common:last-modified-date>2020-07-14T18:58:40.838Z</common:last-modified-date>
  //             <common:source>
  //                 <common:source-orcid>
  //                     <common:uri>https://orcid.org/0000-0003-1301-6629</common:uri>
  //                     <common:path>0000-0003-1301-6629</common:path>
  //                     <common:host>orcid.org</common:host>
  //                 </common:source-orcid>
  //                 <common:source-name>Tangley Polecat</common:source-name>
  //             </common:source>
  //             <email:email>rnorris@gemini.edu</email:email>
  //         </email:email>
  //         <email:email visibility="public" verified="true" primary="false">
  //             <common:created-date>2019-05-13T17:46:19.157Z</common:created-date>
  //             <common:last-modified-date>2020-07-14T18:58:41.444Z</common:last-modified-date>
  //             <common:source>
  //                 <common:source-orcid>
  //                     <common:uri>https://orcid.org/0000-0003-1301-6629</common:uri>
  //                     <common:path>0000-0003-1301-6629</common:path>
  //                     <common:host>orcid.org</common:host>
  //                 </common:source-orcid>
  //                 <common:source-name>Tangley Polecat</common:source-name>
  //             </common:source>
  //             <email:email>rob_norris@mac.com</email:email>
  //         </email:email>
  //     </email:emails>
  //     <address:addresses path="/0000-0003-1301-6629/address"/>
  //     <keyword:keywords path="/0000-0003-1301-6629/keywords"/>
  //     <external-identifier:external-identifiers path="/0000-0003-1301-6629/external-identifiers"/>
  // </person:person>
  def fromXml(personElem: Elem) = {
    val g = (personElem \ "name" \ "given-names").headOption.map(_.text)
    val f = (personElem \ "name" \ "family-name").headOption.map(_.text)
    val c = (personElem \ "name" \ "credit-name").headOption.map(_.text)
    val e = (personElem \ "emails" \ "email").collectFirst { case n if (n \@ "verified") == "true" => (n \ "email").text }
    PersonInfo(g, f, c, e)
  }

  implicit def entityDecoderPersonInfo[F[_]: Sync]: EntityDecoder[F, PersonInfo] =
    EntityDecoder[F, Elem].map(fromXml)

}