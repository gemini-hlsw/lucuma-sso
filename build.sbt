val bcpgVersion                = "1.70"
val circeVersion               = "0.14.1"
val cirisVersion               = "2.3.2"
val declineVersion             = "2.1.0"
val disciplineMunitVersion     = "1.0.9"
val flywayVersion              = "7.11.4"
val grackleVersion             = "0.1.14"
val http4sVersion              = "0.23.7"
val jwtVersion                 = "5.0.0"
val log4catsVersion            = "2.1.1"
val lucumaCoreVersion          = "0.24.0"
val lucumaGraphQLRoutesVersion = "0.1.2"
val munitVersion               = "0.7.29"
val natcchezHttp4sVersion      = "0.3.2"
val natchezVersion             = "0.1.5"
val postgresVersion            = "42.3.2"
val skunkVersion               = "0.2.3"
val slf4jVersion               = "1.7.35"

// If we don't do this we get a spurious warning about an unused key.
Global / excludeLintKeys += scalaJSLinkerConfig

ThisBuild / tlBaseVersion := "0.0"
ThisBuild / tlCiReleaseBranches := Seq("master")
ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Run(List("chmod 600 test-cert/server.key"), name = Some("Set up cert permissions (1)")),
  WorkflowStep.Run(List("sudo chown 999 test-cert/server.key"), name = Some("Set up cert permissions (2)")),
  WorkflowStep.Run(List("docker-compose up -d"), name = Some("Start up Postgres instances")),
)
ThisBuild / githubWorkflowBuild ~= { steps =>
  steps.map {
    case step @ WorkflowStep.Sbt(List("test"), _, _, _, _, _) =>
      step.copy(commands = List("coverage", "test", "coverageReport", "coverageAggregate"))
    case step => step
  }
}
ThisBuild / githubWorkflowBuildPostamble ++= Seq(
  WorkflowStep.Run(List("docker-compose down"), name = Some("Shut down Postgres instances")),
  WorkflowStep.Run(List("bash <(curl -s https://codecov.io/bash)"), name = Some("Upload code coverage data")),
)

// Temporarily due to Scala-XML 2.0.0
ThisBuild / evictionErrorLevel := Level.Info

ThisBuild / libraryDependencies ++= Seq(
  "com.disneystreaming" %% "weaver-cats"       % "0.7.9" % Test,
  "com.disneystreaming" %% "weaver-scalacheck" % "0.7.9" % Test,
)
ThisBuild / testFrameworks += new TestFramework("weaver.framework.CatsEffect")

enablePlugins(NoPublishPlugin)

lazy val frontendClient = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/frontend-client"))
  .settings(
    name := "lucuma-sso-frontend-client",
    libraryDependencies ++= Seq(
      "edu.gemini"    %%% "lucuma-core"         % lucumaCoreVersion,
      "io.circe"      %%% "circe-generic"       % circeVersion,
      "edu.gemini"    %%% "lucuma-core-testkit" % lucumaCoreVersion       % Test,
      "org.scalameta" %%% "munit"               % munitVersion            % Test,
      "org.scalameta" %%% "munit-scalacheck"    % munitVersion            % Test,
      "org.typelevel" %%% "discipline-munit"    % disciplineMunitVersion  % Test,
    )
  )
  .jsSettings(
    coverageEnabled := false,
  )

lazy val backendClient = project
  .in(file("modules/backend-client"))
  .dependsOn(frontendClient.jvm)
  .settings(
    name := "lucuma-sso-backend-client",
    libraryDependencies ++= Seq(
      "com.pauldijou"     %% "jwt-circe"      % jwtVersion,
      "com.pauldijou"     %% "jwt-core"       % jwtVersion,
      "org.bouncycastle"  %  "bcpg-jdk15on"   % bcpgVersion,
      "org.http4s"        %% "http4s-circe"   % http4sVersion,
      "org.http4s"        %% "http4s-circe"   % http4sVersion,
      "org.http4s"        %% "http4s-dsl"     % http4sVersion,
      "org.http4s"        %% "http4s-client"  % http4sVersion,
      "org.typelevel"     %% "log4cats-slf4j" % log4catsVersion,
      "org.tpolecat"      %% "natchez-http4s" % natcchezHttp4sVersion,
    ),
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(backendClient)
  .enablePlugins(NoPublishPlugin, JavaAppPackaging)
  .settings(
    name := "lucuma-sso-service",
    libraryDependencies ++= Seq(
      "io.circe"       %% "circe-parser"        % circeVersion,
      "is.cir"         %% "ciris"               % cirisVersion,
      "org.http4s"     %% "http4s-ember-client" % http4sVersion,
      "org.http4s"     %% "http4s-blaze-server" % http4sVersion,
      "org.http4s"     %% "http4s-scala-xml"    % http4sVersion,
      "org.slf4j"      %  "slf4j-simple"        % slf4jVersion,
      "org.tpolecat"   %% "natchez-honeycomb"   % natchezVersion,
      "org.tpolecat"   %% "natchez-log"         % natchezVersion,
      "org.tpolecat"   %% "natchez-http4s"      % natcchezHttp4sVersion,
      "org.tpolecat"   %% "skunk-core"          % skunkVersion,
      "org.flywaydb"   %  "flyway-core"         % flywayVersion,
      "org.postgresql" %  "postgresql"          % postgresVersion,
      "com.monovore"   %% "decline-effect"      % declineVersion,
      "com.monovore"   %% "decline"             % declineVersion,
      "edu.gemini"     %% "gsp-graphql-skunk"   % grackleVersion,
      "edu.gemini"     %% "lucuma-graphql-routes-grackle" % lucumaGraphQLRoutesVersion,
      "io.circe"       %% "circe-literal"       % circeVersion       % Test,
    ),
  )

lazy val backendExample = project
  .in(file("modules/backend-example"))
  .enablePlugins(NoPublishPlugin)
  .dependsOn(backendClient)
  .settings(
    name := "lucuma-sso-backend-example",
    libraryDependencies ++= Seq(
      "is.cir"       %% "ciris"               % cirisVersion,
      "org.http4s"   %% "http4s-ember-client" % http4sVersion,
      "org.http4s"   %% "http4s-ember-server" % http4sVersion,
      "org.slf4j"    %  "slf4j-simple"        % slf4jVersion,
      "org.tpolecat" %% "natchez-honeycomb"   % natchezVersion,
    )
  )
