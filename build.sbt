import sbtcrossproject.CrossType

// If we don't do this we get a spurious warning about an unused key.
Global / excludeLintKeys += scalaJSLinkerConfig

// Temporarily due to Scala-XML 2.0.0
ThisBuild / evictionErrorLevel := Level.Info

inThisBuild(Seq(
  homepage := Some(url("https://github.com/gemini-hlsw/lucuma-sso")),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "com.disneystreaming" %% "weaver-cats"       % "0.7.3" % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % "0.7.3" % Test,
   ),
  testFrameworks += new TestFramework("weaver.framework.CatsEffect") ,
) ++ lucumaPublishSettings)

publish / skip := true

lazy val frontendClient = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/frontend-client"))
  .settings(
    name := "lucuma-sso-frontend-client",
    libraryDependencies ++= Seq(
      "edu.gemini"    %%% "lucuma-core"         % "0.8.1",
      "io.circe"      %%% "circe-generic"       % "0.14.1",
      "edu.gemini"    %%% "lucuma-core-testkit" % "0.8.1"  % Test,
      "org.scalameta" %%% "munit"               % "0.7.26" % Test,
      "org.scalameta" %%% "munit-scalacheck"    % "0.7.26" % Test,
      "org.typelevel" %%% "discipline-munit"    % "1.0.9"  % Test,
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
      "com.pauldijou"     %% "jwt-circe"      % "5.0.0",
      "com.pauldijou"     %% "jwt-core"       % "5.0.0",
      "org.bouncycastle"  %  "bcpg-jdk15on"   % "1.68",
      "org.http4s"        %% "http4s-circe"   % "0.23.0-M1",
      "org.http4s"        %% "http4s-circe"   % "0.23.0-M1",
      "org.http4s"        %% "http4s-dsl"     % "0.23.0-M1",
      "org.http4s"        %% "http4s-client"  % "0.23.0-M1",
      "org.typelevel"     %% "log4cats-slf4j" % "2.1.1",
      "org.tpolecat"      %% "natchez-http4s" % "0.1.0",
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
      "io.circe"       %% "circe-parser"        % "0.14.1",
      "is.cir"         %% "ciris"               % "2.0.0",
      "org.http4s"     %% "http4s-ember-client" % "0.23.0-M1",
      "org.http4s"     %% "http4s-ember-server" % "0.23.0-M1",
      "org.http4s"     %% "http4s-scala-xml"    % "0.23.0-M1",
      "org.slf4j"      %  "slf4j-simple"        % "1.7.30",
      "org.tpolecat"   %% "natchez-honeycomb"   % "0.1.5",
      "org.tpolecat"   %% "natchez-log"         % "0.1.5",
      "org.tpolecat"   %% "natchez-http4s"      % "0.1.2",
      "org.tpolecat"   %% "skunk-core"          % "0.1.2",
      "org.flywaydb"   %  "flyway-core"         % "7.9.1",
      "org.postgresql" %  "postgresql"          % "42.2.23",
      "com.monovore"   %% "decline-effect"      % "2.0.0",
      "com.monovore"   %% "decline"             % "2.0.0",
      "edu.gemini"     %% "gsp-graphql-skunk"   % "0.0.47+16-59a79b5f+20210527-1206-SNAPSHOT",
      "io.circe"       %% "circe-literal"       % "0.14.1" % "test",
    )
  )

lazy val backendExample = project
  .in(file("modules/backend-example"))
  .dependsOn(backendClient)
  .settings(
    publish / skip := true,
    name := "lucuma-sso-backend-example",
    libraryDependencies ++= Seq(
      "is.cir"       %% "ciris"               % "2.0.0",
      "org.http4s"   %% "http4s-ember-client" % "0.23.0-M1",
      "org.http4s"   %% "http4s-ember-server" % "0.23.0-M1",
      "org.slf4j"    %  "slf4j-simple"        % "1.7.31",
      "org.tpolecat" %% "natchez-honeycomb"   % "0.1.5",
    )
  )