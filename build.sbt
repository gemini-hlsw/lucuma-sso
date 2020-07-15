import sbtcrossproject.crossProject
import sbtcrossproject.CrossType

inThisBuild(Seq(
  homepage := Some(url("https://github.com/gemini-hlsw/gpp-sso")),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
) ++ gspPublishSettings)

skip in publish := true

lazy val model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .settings(
    name := "gpp-sso-model",
    libraryDependencies ++= Seq(
      "edu.gemini" %%% "gsp-core-model" % "0.1.5",
    )
  )
  .jvmConfigure(_.enablePlugins(AutomateHeaderPlugin))
  .jsSettings(gspScalaJsSettings: _*)
  .jsSettings(
    // libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(model.jvm)
  .settings(
    name := "gpp-sso-service",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client" % "0.21.6",
      "org.http4s" %% "http4s-circe"        % "0.21.6",
      "io.circe"   %% "circe-generic"       % "0.13.0",
      "io.circe"   %% "circe-parser"        % "0.13.0",
    ),
  )

