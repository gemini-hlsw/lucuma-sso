import sbtcrossproject.CrossType

val bcpgVersion            = "1.69"
val circeVersion           = "0.14.1"
val cirisVersion           = "2.0.1"
val declineVersion         = "2.1.0"
val disciplineMunitVersion = "1.0.9"
val flywayVersion          = "7.11.4"
val grackleVersion         = "0.1.1"
val http4sVersion          = "0.23.0-RC1"
val jwtVersion             = "5.0.0"
val log4catsVersion        = "2.1.1"
val lucumaCoreVersion      = "0.14.0"
val munitVersion           = "0.7.29"
val natcchezHttp4sVersion  = "0.1.3"
val natchezVersion         = "0.1.5"
val postgresVersion        = "42.2.24"
val skunkVersion           = "0.2.2"
val slf4jVersion           = "1.7.32"

// If we don't do this we get a spurious warning about an unused key.
Global / excludeLintKeys += scalaJSLinkerConfig

// Temporarily due to Scala-XML 2.0.0
ThisBuild / evictionErrorLevel := Level.Info

inThisBuild(Seq(
  homepage := Some(url("https://github.com/gemini-hlsw/lucuma-sso")),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "com.disneystreaming" %% "weaver-cats"       % "0.7.6" % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % "0.7.6" % Test,
   ),
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
) ++ lucumaPublishSettings)

publish / skip := true

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
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
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
  .enablePlugins(JavaAppPackaging)
  .settings(
    publish / skip := true,
    name := "lucuma-sso-service",
    libraryDependencies ++= Seq(
      "io.circe"       %% "circe-parser"        % circeVersion,
      "is.cir"         %% "ciris"               % cirisVersion,
      "org.http4s"     %% "http4s-ember-client" % http4sVersion,
      "org.http4s"     %% "http4s-ember-server" % http4sVersion,
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
      "io.circe"       %% "circe-literal"       % circeVersion       % Test,
    )
  )

lazy val backendExample = project
  .in(file("modules/backend-example"))
  .dependsOn(backendClient)
  .settings(
    publish / skip := true,
    name := "lucuma-sso-backend-example",
    libraryDependencies ++= Seq(
      "is.cir"       %% "ciris"               % cirisVersion,
      "org.http4s"   %% "http4s-ember-client" % http4sVersion,
      "org.http4s"   %% "http4s-ember-server" % http4sVersion,
      "org.slf4j"    %  "slf4j-simple"        % slf4jVersion,
      "org.tpolecat" %% "natchez-honeycomb"   % natchezVersion,
    )
  )