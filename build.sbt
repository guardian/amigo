import akka.actor.FSM.->
import com.gu.riffraff.artifact.BuildInfo
import com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd

name := "amigo"
version := "1.0-latest"
scalaVersion := "2.11.8"

javaOptions in Universal ++= Seq(
  s"-Dpidfile.path=/dev/null",
  "-J-XX:MaxRAMFraction=2",
  "-J-XX:InitialRAMFraction=2",
  "-J-XX:MaxMetaspaceSize=300m",
  "-J-XX:+PrintGCDetails",
  "-J-XX:+PrintGCDateStamps",
  "-J-DpackerHome=/opt/packer",
  "-J-Dlogger.resource=logback-PROD.xml",
  s"-J-Dlogs.home=/var/log/${packageName.value}",
  s"-J-Xloggc:/var/log/${packageName.value}/gc.log"
)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, RiffRaffArtifact, JDebPackaging, BuildInfoPlugin)
  .settings(
    packageName in Universal := normalizedName.value,
    maintainer := "Guardian Developer Experience <devx@theguardian.com>",

    serverLoading in Debian := Systemd,
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffArtifactResources ++= Seq(
      (packageBin in Universal in imageCopier).value -> "imagecopier/imagecopier.zip",
      baseDirectory.value / "cdk/cdk.out/AMIgo.template.json" -> "cloudformation/AMIgo.template.json"
    ),
    // Include the roles dir in the tarball for now
    mappings in Universal ++= (file("roles") ** "*").get.map { f => f.getAbsoluteFile -> f.toString },
    buildInfoPackage := "amigo",
    buildInfoKeys := {
      lazy val buildInfo = BuildInfo(baseDirectory.value)
      Seq[BuildInfoKey](
        BuildInfoKey.constant("buildNumber", buildInfo.buildIdentifier),
        // so this next one is constant to avoid it always recompiling on dev machines.
        // we only really care about build time on teamcity, when a constant based on when
        // it was loaded is just fine
        BuildInfoKey.constant("buildTime", System.currentTimeMillis),
        BuildInfoKey.constant("gitCommitId", buildInfo.revision)
      )
    }
  )

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

val jacksonVersion = "2.7.1"
val awsVersion = "1.11.1017"
val circeVersion = "0.9.0"
libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.gu" %% "scanamo" % "0.9.5",
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
  "com.amazonaws" % "aws-java-sdk-sts" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-kinesis" % awsVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "5.1",
  "com.gu" % "kinesis-logback-appender" % "1.4.2",
  "org.scalatest" %% "scalatest" % "2.2.6" % Test,
  "org.mockito" % "mockito-core" % "2.7.19" % Test,
  "fun.mike" % "diff-match-patch" % "0.0.2",
  "com.gu" % "anghammarad-client_2.11" % "1.1.3"
)
routesGenerator := InjectedRoutesGenerator
routesImport += "models._"

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
