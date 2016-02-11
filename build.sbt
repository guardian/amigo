name := "amigo"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.7"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
libraryDependencies ++= Seq(
  ws
)
routesGenerator := InjectedRoutesGenerator
