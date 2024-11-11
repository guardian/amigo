addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.5")

// sbt-native-packager cannot be updated to >1.9.9 until Play supports scala-xml 2
addSbtPlugin(
  "com.github.sbt" % "sbt-native-packager" % "1.10.4"
) // scala-steward:off
addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.1.18")
libraryDependencies += "org.vafer" % "jdeb" % "1.11" artifacts Artifact(
  "jdeb",
  "jar",
  "jar"
)
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

/*
 * This is required for Scala Steward to run until SBT plugins all migrated to scala-xml 2.
 * See https://github.com/scala-steward-org/scala-steward/blob/13d63e8ae98a714efcdac2c7af18f004130512fa/project/plugins.sbt#L16-L19
 */
libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
