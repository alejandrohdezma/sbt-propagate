ThisBuild / scalaVersion                  := _root_.scalafix.sbt.BuildInfo.scala212
ThisBuild / organization                  := "com.alejandrohdezma"
ThisBuild / pluginCrossBuild / sbtVersion := "1.2.8"
ThisBuild / versionPolicyIntention        := Compatibility.BinaryAndSourceCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; scripted")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .enablePlugins(MdocPlugin, SbtPlugin)
  .dependsOn(`sbt-propagate`, `resource-generator`)
  .settings(addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.6.1"))

lazy val `sbt-propagate` = module
  .enablePlugins(SbtPlugin)
  .settings(scriptedBatchExecution := false)
  .settings(scriptedBufferLog := false)
  .settings(scriptedLaunchOpts += s"-Dplugin.version=${version.value}")
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoPackage := "com.alejandrohdezma.sbt.propagate")

lazy val `resource-generator` = module
  .settings(crossScalaVersions := Seq("2.12.20", "2.13.15", "3.3.4"))
