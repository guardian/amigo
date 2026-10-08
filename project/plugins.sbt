addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.12")

addSbtPlugin(
  "com.github.sbt" % "sbt-native-packager" % "1.13.0"
)
libraryDependencies += "org.vafer" % "jdeb" % "1.14" artifacts Artifact(
  "jdeb",
  "jar",
  "jar"
)
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.2")
addDependencyTreePlugin

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

/*
 * This is required for Scala Steward to run until SBT plugins all migrated to scala-xml 2.
 * See https://github.com/scala-steward-org/scala-steward/blob/13d63e8ae98a714efcdac2c7af18f004130512fa/project/plugins.sbt#L16-L19
 */
libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
