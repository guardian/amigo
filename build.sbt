name := "amigo"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.7"

lazy val root = (project in file(".")).enablePlugins(PlayScala, RiffRaffArtifact)

val jacksonVersion = "2.7.1"
libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.gu" %% "scanamo" % "0.1.0",
  "com.github.cb372" %% "automagic" % "0.1",
  "com.beachape" %% "enumeratum" % "1.3.7",
  "com.typesafe.akka" %% "akka-typed-experimental" % "2.4.2",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)
routesGenerator := InjectedRoutesGenerator
routesImport += "models._"

riffRaffPackageType := (packageZipTarball in Universal).value
riffRaffBuildIdentifier := sys.env.getOrElse("TRAVIS_BUILD_NUMBER", "DEV")
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")

// Include the roles dir in the tarball for now
mappings in Universal ++= (file("roles") ** "*").get.map { f => f.getAbsoluteFile -> f.toString }

scalariformSettings
