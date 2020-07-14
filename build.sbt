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
      // "org.tpolecat"               %%% "atto-core"               % attoVersion,
      // "org.typelevel"              %%% "cats-core"               % catsVersion,
      // "com.github.julien-truffaut" %%% "monocle-core"            % monocleVersion,
      // "com.github.julien-truffaut" %%% "monocle-macro"           % monocleVersion,
      // "org.scala-lang.modules"     %%% "scala-collection-compat" % collCompatVersion
    )
  )
  .jvmConfigure(_.enablePlugins(AutomateHeaderPlugin))
  .jsSettings(gspScalaJsSettings: _*)
  .jsSettings(
    // libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion
  )


