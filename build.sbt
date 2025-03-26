val bcpgVersion                = "1.77"
val circeVersion               = "0.14.12"
val cirisVersion               = "3.7.0"
val declineVersion             = "2.5.0"
val disciplineMunitVersion     = "2.0.0"
val flywayVersion              = "11.5.0"
val grackleVersion             = "0.23.0"
val http4sVersion              = "0.23.30"
val http4sBlazeVersion         = "0.23.17"
val http4sEmberVersion         = "0.23.30"
val http4sXmlVersion           = "0.23.14"
val jwtVersion                 = "10.0.4"
val log4catsVersion            = "2.7.0"
val lucumaCoreVersion          = "0.120.0"
val lucumaGraphQLRoutesVersion = "0.8.17"
val munitVersion               = "1.1.0"
val munitScalacheckVersion     = "1.1.0"
val natcchezHttp4sVersion      = "0.6.1"
val natchezVersion             = "0.3.7"
val postgresVersion            = "42.7.5"
val skunkVersion               = "0.6.4"
val slf4jVersion               = "2.0.17"
val weaverVersion              = "0.8.4"

// If we don't do this we get a spurious warning about an unused key.
Global / excludeLintKeys += scalaJSLinkerConfig

ThisBuild / tlBaseVersion := "0.8"
ThisBuild / scalaVersion       := "3.6.4"
ThisBuild / crossScalaVersions := Seq("3.6.4")
ThisBuild / scalacOptions ++= Seq(
  "-language:implicitConversions"
)

ThisBuild / tlCiReleaseBranches := Seq("master", "scala3")
ThisBuild / githubWorkflowBuildPreamble ~= { steps =>
  Seq(
    WorkflowStep.Run(List("chmod 600 test-cert/server.key"), name = Some("Set up cert permissions (1)")),
    WorkflowStep.Run(List("sudo chown 999 test-cert/server.key"), name = Some("Set up cert permissions (2)")),
  ) ++ steps
}

// Temporarily due to Scala-XML 2.0.0
ThisBuild / evictionErrorLevel := Level.Info

ThisBuild / testFrameworks += new TestFramework("weaver.framework.CatsEffect")

lazy val root = tlCrossRootProject.aggregate(
  frontendClient,
  backendClient,
  service,
  backendExample,
)

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
      "org.scalameta" %%% "munit-scalacheck"    % munitScalacheckVersion  % Test,
      "org.typelevel" %%% "discipline-munit"    % disciplineMunitVersion  % Test,
    )
  )

lazy val backendClient = project
  .in(file("modules/backend-client"))
  .dependsOn(frontendClient.jvm)
  .settings(
    name := "lucuma-sso-backend-client",
    libraryDependencies ++= Seq(
      "com.github.jwt-scala" %% "jwt-core"       % jwtVersion,
      "com.github.jwt-scala" %% "jwt-circe"      % jwtVersion,
      "org.bouncycastle"     %  "bcpg-jdk18on"   % bcpgVersion,
      "org.http4s"           %% "http4s-circe"   % http4sVersion,
      "org.http4s"           %% "http4s-dsl"     % http4sVersion,
      "org.http4s"           %% "http4s-client"  % http4sVersion,
      "org.typelevel"        %% "log4cats-slf4j" % log4catsVersion,
      "org.tpolecat"         %% "natchez-http4s" % natcchezHttp4sVersion,
    ),
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(backendClient)
  .enablePlugins(NoPublishPlugin, JavaAppPackaging)
  .settings(
    name := "lucuma-sso-service",
    libraryDependencies ++= Seq(
      "io.circe"       %% "circe-parser"           % circeVersion,
      "is.cir"         %% "ciris"                  % cirisVersion,
      "org.http4s"     %% "http4s-blaze-server"    % http4sBlazeVersion,
      "org.http4s"     %% "http4s-ember-client"    % http4sEmberVersion,
      "org.http4s"     %% "http4s-scala-xml"       % http4sXmlVersion,
      "org.slf4j"      %  "slf4j-simple"           % slf4jVersion,
      "org.tpolecat"   %% "natchez-honeycomb"      % natchezVersion,
      "org.tpolecat"   %% "natchez-log"            % natchezVersion,
      "org.tpolecat"   %% "natchez-http4s"         % natcchezHttp4sVersion,
      "org.tpolecat"   %% "skunk-core"             % skunkVersion,
      "org.flywaydb"   %  "flyway-core"            % flywayVersion,
      "org.postgresql" %  "postgresql"             % postgresVersion,
      "com.monovore"   %% "decline-effect"         % declineVersion,
      "com.monovore"   %% "decline"                % declineVersion,
      "org.typelevel"  %% "grackle-skunk"          % grackleVersion,
      "edu.gemini"     %% "lucuma-graphql-routes"  % lucumaGraphQLRoutesVersion,
      "io.circe"       %% "circe-literal"          % circeVersion       % Test,
      "com.disneystreaming" %% "weaver-cats"       % weaverVersion % Test,
      "com.disneystreaming" %% "weaver-scalacheck" % weaverVersion % Test,
    ),
    reStart / envVars += "PORT" -> "8082",
    reStartArgs       += "serve"
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
