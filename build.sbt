name := "amigo"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala, RiffRaffArtifact)

def getTravisBranch(): String = {
  sys.env.get("TRAVIS_PULL_REQUEST") match {
    case Some("false") => sys.env.getOrElse("TRAVIS_BRANCH", "unknown-branch")
    case Some(i) => s"pr/$i"
    case None => "unknown-branch"
  }
}

val jacksonVersion = "2.7.1"
libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.gu" %% "scanamo" % "0.7.0",
  "com.github.cb372" %% "automagic" % "0.1",
  "com.beachape" %% "enumeratum" % "1.3.7",
  "com.typesafe.akka" %% "akka-typed-experimental" % "2.4.2",
  "com.gu" %% "configuration-magic-play2-4" % "1.2.0",
  "com.gu" %% "play-googleauth" % "0.4.0",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.5-P24",
  "org.quartz-scheduler" % "quartz" % "2.2.3",
  "org.scalatest" %% "scalatest" % "2.2.6" % Test
)
routesGenerator := InjectedRoutesGenerator
routesImport += "models._"

riffRaffPackageType := (packageZipTarball in Universal).value
riffRaffBuildIdentifier := sys.env.getOrElse("TRAVIS_BUILD_NUMBER", "DEV")
riffRaffManifestBranch := getTravisBranch()
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")

// Include the roles dir in the tarball for now
mappings in Universal ++= (file("roles") ** "*").get.map { f => f.getAbsoluteFile -> f.toString }

scalariformSettings
