import sbtcrossproject.CrossType

inThisBuild(Seq(
  homepage := Some(url("https://github.com/gemini-hlsw/lucuma-sso")),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "com.disneystreaming" %% "weaver-framework"  % "0.5.1" % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % "0.5.1" % Test,
  ),
  testFrameworks += new TestFramework("weaver.framework.TestFramework"),
) ++ lucumaPublishSettings)

skip in publish := true

lazy val frontendClient = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/frontend-client"))
  .settings(
    name := "lucuma-sso-frontend-client",
    libraryDependencies ++= Seq(
      "edu.gemini"    %%% "lucuma-core"         % "0.7.6",
      "io.circe"      %%% "circe-generic"       % "0.13.0",
      "edu.gemini"    %%% "lucuma-core-testkit" % "0.7.6"  % Test,
      "org.scalameta" %%% "munit"               % "0.7.21" % Test,
      "org.scalameta" %%% "munit-scalacheck"    % "0.7.21" % Test,
      "org.typelevel" %%% "discipline-munit"    % "1.0.5"  % Test,
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
      "org.http4s"        %% "http4s-circe"   % "0.21.19",
      "org.http4s"        %% "http4s-circe"   % "0.21.19",
      "org.http4s"        %% "http4s-dsl"     % "0.21.19",
      "org.http4s"        %% "http4s-client"  % "0.21.19",
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1",
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
      "io.circe"       %% "circe-parser"        % "0.13.0",
      "is.cir"         %% "ciris"               % "1.2.1",
      "org.http4s"     %% "http4s-ember-client" % "0.21.19",
      "org.http4s"     %% "http4s-ember-server" % "0.21.19",
      "org.http4s"     %% "http4s-scala-xml"    % "0.21.19",
      "org.slf4j"      %  "slf4j-simple"        % "1.7.30",
      "org.tpolecat"   %% "natchez-honeycomb"   % "0.0.19",
      "org.tpolecat"   %% "natchez-log"         % "0.0.19",
      "org.tpolecat"   %% "natchez-http4s"      % "0.0.2",
      "org.tpolecat"   %% "skunk-core"          % "0.0.23",
      "org.flywaydb"   %  "flyway-core"         % "7.5.3",
      "org.postgresql" %  "postgresql"          % "42.2.19",
      "com.monovore"   %% "decline-effect"      % "1.3.0",
      "com.monovore"   %% "decline"             % "1.3.0",
    )
  )

lazy val backendExample = project
  .in(file("modules/backend-example"))
  .dependsOn(backendClient)
  .settings(
    publish / skip := true,
    name := "lucuma-sso-backend-example",
    libraryDependencies ++= Seq(
      "is.cir"     %% "ciris"               % "1.2.1",
      "org.http4s" %% "http4s-ember-client" % "0.21.19",
      "org.http4s" %% "http4s-ember-server" % "0.21.19",
      "org.slf4j"  %  "slf4j-simple"        % "1.7.30",
    )
  )