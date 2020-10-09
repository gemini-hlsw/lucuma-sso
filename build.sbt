import sbtcrossproject.crossProject
import sbtcrossproject.CrossType
import com.timushev.sbt.updates.UpdatesKeys.dependencyUpdatesFilter

inThisBuild(Seq(
  homepage := Some(url("https://github.com/gemini-hlsw/lucuma-sso")),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "com.disneystreaming" %% "weaver-framework"  % "0.4.3" % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % "0.4.3" % Test,
  ),
  testFrameworks += new TestFramework("weaver.framework.TestFramework"),
) ++ gspPublishSettings)

skip in publish := true

lazy val model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .settings(
    name := "lucuma-sso-model",
    libraryDependencies ++= Seq(
      "edu.gemini" %%% "lucuma-core"    % "0.4.5",
      "io.circe"   %%% "circe-generic"  % "0.13.0",
    )
  )
  .jvmConfigure(_.enablePlugins(AutomateHeaderPlugin))
  .jsSettings(gspScalaJsSettings: _*)

lazy val client = project
  .in(file("modules/client"))
  .dependsOn(model.jvm)
  .settings(
    name := "lucuma-sso-client",
    libraryDependencies ++= Seq(
      "com.pauldijou"    %% "jwt-circe"           % "4.3.0",
      "com.pauldijou"    %% "jwt-core"            % "4.3.0",
      "org.bouncycastle" %  "bcpg-jdk15on"        % "1.66",
      "org.http4s"       %% "http4s-circe"        % "0.21.7+17-2e3f5550-SNAPSHOT",
    )
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(model.jvm, client)
  .enablePlugins(JavaAppPackaging)
  .settings(
    publish / skip := true,
    name := "lucuma-sso-service",
    libraryDependencies ++= Seq(
      "io.circe"               %% "circe-parser"        % "0.13.0",
      "is.cir"                 %% "ciris"               % "1.2.1",
      "org.http4s"             %% "http4s-dsl"          % "0.21.7+17-2e3f5550-SNAPSHOT",
      "org.http4s"             %% "http4s-ember-client" % "0.21.7+17-2e3f5550-SNAPSHOT",
      "org.http4s"             %% "http4s-ember-server" % "0.21.7+17-2e3f5550-SNAPSHOT",
      "org.http4s"             %% "http4s-scala-xml"    % "0.21.7+17-2e3f5550-SNAPSHOT",
      "org.slf4j"              %  "slf4j-simple"        % "1.7.30",
      "org.tpolecat"           %% "natchez-jaeger"      % "0.0.12",
      "org.tpolecat"           %% "skunk-core"          % "0.0.21",
      // We use JDBC to do migrations
      "org.flywaydb"           % "flyway-core"          % "6.5.7",
      "org.postgresql"         % "postgresql"           % "42.2.17",
    ),

  )


