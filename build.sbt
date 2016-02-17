name := "amigo"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.7"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

val jacksonVersion = "2.7.1"
libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)
routesGenerator := InjectedRoutesGenerator

scalariformSettings
