// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.config

import cats.implicits._
import ciris._
import java.util.UUID
import java.time.LocalDateTime
import lucuma.sso.service.config.HerokuConfig.Review
import lucuma.sso.service.config.HerokuConfig.Default

sealed trait HerokuConfig {

  def appName: String

  def versionText: String =
    this match {
      case Review(_, branch, prNumber) => s"Pull Request #$prNumber from $branch"
      case Default(_, _, _, releaseCreatedAt, releaseVersion, slugCommit, _) =>
        s"$releaseVersion/$slugCommit created at $releaseCreatedAt"
    }

}

object HerokuConfig {

  /** Configuration provided for review apps.
    * @param appName  The name of the review app.
    * @param branch   The name of the remote branch the review app is tracking.
    * @param prNumber The GitHub Pull Request number if the review app is created automatically.
    * @see https://devcenter.heroku.com/articles/github-integration-review-apps#injected-environment-variables
    */
  case class Review(
    appName:  String,
    branch:   String,
    prNumber: Option[Int],
  ) extends HerokuConfig

  /** Configuration provided for staging and production.
    * @param appId	          The unique identifier for the application.
    * @param appName	        The application name.
    * @param dynoId	          The dyno identifier.
    * @param releaseCreatedAt	The time and date the release was created.
    * @param releaseVersion	  The identifier for the current release.
    * @param slugCommit	      The commit hash for the current release.
    * @param slugDescription	The description of the current release.
    * @see https://devcenter.heroku.com/articles/dyno-metadata
    */
  case class Default(
    appId:            UUID,
    appName:          String,
    dynoId:           UUID,
    releaseCreatedAt: LocalDateTime,
    releaseVersion:   String,
    slugCommit:       String,
    slugDescription:  String,
  ) extends HerokuConfig

  val review: ConfigValue[Review] = (
    env("HEROKU_APP_NAME"),
    env("HEROKU_BRANCH"),
    env("HEROKU_PR_NUMBER").as[Int].option,
  ).parMapN(Review)

  val default: ConfigValue[Default] = (
    env("HEROKU_APP_ID").as[UUID],
    env("HEROKU_APP_NAME"),
    env("HEROKU_DYNO_ID").as[UUID],
    env("HEROKU_RELEASE_CREATED_AT").as(isoLocalDateTime),
    env("HEROKU_RELEASE_VERSION"),
    env("HEROKU_SLUG_COMMIT"),
    env("HEROKU_SLUG_DESCRIPTION"),
  ).parMapN(Default)

  val config: ConfigValue[HerokuConfig] =
    // default.widen or review.widen
    review.widen

}


