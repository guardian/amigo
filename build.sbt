name := "amigo"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala, RiffRaffArtifact)

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

def getTravisBranch(): String = {
  sys.env.get("TRAVIS_PULL_REQUEST") match {
    case Some("false") => sys.env.getOrElse("TRAVIS_BRANCH", "unknown-branch")
    case Some(i) => s"pr/$i"
    case None => "unknown-branch"
  }
}

val jacksonVersion = "2.7.1"
val awsVersion = "1.11.263"
val circeVersion = "0.9.0"
libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.gu" %% "scanamo" % "0.9.2",
  "com.github.cb372" %% "automagic" % "0.1",
  "com.beachape" %% "enumeratum" % "1.3.7",
  "com.typesafe.akka" %% "akka-typed-experimental" % "2.4.2",
  "com.typesafe.akka" %% "akka-agent" % "2.4.2",
  "com.gu" %% "configuration-magic-play2-4" % "1.3.0",
  "com.gu" %% "play-googleauth" % "0.4.0",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.5-P24",
  "org.quartz-scheduler" % "quartz" % "2.2.3",
  "com.lihaoyi" %% "fastparse" % "0.4.1",
  "com.amazonaws" % "aws-java-sdk-sns" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % awsVersion,
  "org.scalatest" %% "scalatest" % "2.2.6" % Test,
  "org.mockito" % "mockito-core" % "2.7.19" % Test
)
routesGenerator := InjectedRoutesGenerator
routesImport += "models._"

riffRaffPackageType := (packageZipTarball in Universal).value
riffRaffBuildIdentifier := sys.env.getOrElse("TRAVIS_BUILD_NUMBER", "DEV")
riffRaffManifestBranch := getTravisBranch()
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources ++= Seq(
  (packageBin in Universal in imageCopier).value -> "imagecopier/imagecopier.zip"
)

// Include the roles dir in the tarball for now
mappings in Universal ++= (file("roles") ** "*").get.map { f => f.getAbsoluteFile -> f.toString }

scalariformSettings


lazy val imageCopier = (project in file("imageCopier"))
    .enablePlugins(JavaAppPackaging)
  .settings(
    topLevelDirectory in Universal := None,
    packageName in Universal := normalizedName.value,
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-ec2" % awsVersion,
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
      "com.amazonaws" % "aws-lambda-java-events" % "2.0.2",
      "com.amazonaws" % "aws-lambda-java-log4j" % "1.0.0",
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )
