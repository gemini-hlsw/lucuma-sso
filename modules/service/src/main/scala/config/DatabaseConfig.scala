package gpp.ssp.service.config

import java.net.URI
import ciris.ConfigDecoder
import cats.Show

case class DatabaseConfig(
  host: String,
  port: Int,
  database: String,
  user: String,
  password: Option[String]
)

object DatabaseConfig {

  val Local  = DatabaseConfig(
    host     = "localhost",
    port     = 5432,
    database = "gpp-sso",
    user     = "postgres",
    password = None,
  )

  // postgres://username:password@host:port/database name
  def fromHerokuUri(uri: URI): Option[DatabaseConfig] =
    uri.getUserInfo.split(":") match {
      case Array(user, password) =>
        Some(DatabaseConfig(
          host     = uri.getHost,
          port     = uri.getPort,
          database = uri.getPath.drop(1),
          user     = user,
          password = Some(password),
        ))
      case _ => None
    }

  private implicit val ShowURI: Show[URI] = Show.fromToString

  implicit val ConfigDecoderDatabaseConfig: ConfigDecoder[URI, DatabaseConfig] =
    ConfigDecoder[URI].mapOption("DatabaseConfig")(fromHerokuUri)

}