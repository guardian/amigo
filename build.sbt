import com.gu.riffraff.artifact.BuildInfo
import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

name := "amigo"
version := "1.0-latest"
scalaVersion := "2.12.0"

javaOptions in Universal ++= Seq(
  s"-Dpidfile.path=/dev/null",
  "-J-XX:MaxRAMFraction=2",
  "-J-XX:InitialRAMFraction=2",
  "-J-XX:MaxMetaspaceSize=300m",
  "-J-DpackerHome=/opt/packer",
  "-J-Dlogger.resource=logback-PROD.xml",
  s"-J-Dlogs.home=/var/log/${packageName.value}",
  "-J-Xlog:gc*",
  s"-J-Xlog:gc:/var/log/${packageName.value}/gc.log"
)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, RiffRaffArtifact, JDebPackaging, BuildInfoPlugin, SystemdPlugin)
  .settings(
    packageName in Universal := normalizedName.value,
    maintainer := "Guardian Developer Experience <devx@theguardian.com>",

    serverLoading in Debian := Some(Systemd),
    riffRaffManifestProjectName := s"tools::${name.value}",
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffArtifactResources ++= Seq(
      (packageBin in Universal in imageCopier).value -> "imagecopier/imagecopier.zip",
      baseDirectory.value / "cdk/cdk.out/AMIgo.template.json" -> "cloudformation/AMIgo.template.json"
    ),
    // Include the roles dir in the tarball for now
    mappings in Universal ++= (file("roles") ** "*").get.map { f => f.getAbsoluteFile -> f.toString },
    buildInfoPackage := "amigo",
    buildInfoKeys := {
      lazy val buildInfo = BuildInfo(baseDirectory.value)

      // so this next one is constant to avoid it always recompiling on dev machines.
      // we only really care about build time on teamcity, when a constant based on when
      // it was loaded is just fine
      lazy val buildTime: String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("UTC")))

      Seq[BuildInfoKey](
        BuildInfoKey.constant("buildNumber", buildInfo.buildIdentifier),
        BuildInfoKey.constant("buildTime", buildTime),
        BuildInfoKey.constant("gitCommitId", buildInfo.revision)
      )
    },
    buildInfoOptions:= Seq(
      BuildInfoOption.Traits("management.BuildInfo"),
      BuildInfoOption.ToJson
    )
  )

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

val jacksonVersion = "2.13.1"
val awsVersion = "1.11.1017"
val circeVersion = "0.9.0"

// These can live in the same codebase, see: https://aws.amazon.com/blogs/developer/aws-sdk-for-java-2-x-released/
val awsV1SdkVersion = "1.11.1017"
val awsV2SdkVersion = "2.17.117"

libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.gu" %% "scanamo" % "1.0.0-M4",
  "com.beachape" %% "enumeratum" % "1.6.1",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.5.26",
  "com.gu" %% "simple-configuration-ssm" % "1.5.6",
  "com.typesafe.play" %% "play-iteratees" % "2.6.1",
  "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1",
  "com.gu" %% "play-googleauth" % "0.7.6",
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P26-B3",
  "org.quartz-scheduler" % "quartz" % "2.3.2",
  "com.lihaoyi" %% "fastparse" % "0.4.4",
  "com.amazonaws" % "aws-java-sdk-ec2" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-sns" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-kinesis" % awsV1SdkVersion,
  // These v2 dependencies are pinned to avoid a transitive dependency brought in via simple-configuration-ssm
  // When we upgrade to the Scala 2.12 (or above) and the latest version of simple-configuration-ssm we should
  // be able to remove them.
  "software.amazon.awssdk" % "ec2" % awsV2SdkVersion,
  "software.amazon.awssdk" % "autoscaling" % awsV2SdkVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "5.1",
  "com.gu" % "kinesis-logback-appender" % "1.4.2",
  "org.scalatest" %% "scalatest-flatspec" % "3.2.11" % Test,
  "org.scalatest" %% "scalatest-shouldmatchers" % "3.2.11" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,
  "fun.mike" % "diff-match-patch" % "0.0.2",
  "com.gu" %% "anghammarad-client" % "1.1.3"
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
      "com.amazonaws" % "aws-java-sdk-ec2" % awsV1SdkVersion,
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
      "com.amazonaws" % "aws-lambda-java-events" % "2.0.2",
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )
