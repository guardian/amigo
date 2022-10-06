addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.17")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")
// sbt-native-packager cannot be updated to >1.9.9 until Play supports scala-xml 2
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")  // scala-steward:off
addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.1.18")
libraryDependencies += "org.vafer" % "jdeb" % "1.10" artifacts Artifact("jdeb", "jar", "jar")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
