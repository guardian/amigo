import com.gu.riffraff.artifact.BuildInfo
import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

name := "amigo"
version := "1.0-latest"
scalaVersion := "2.13.10"

Universal / javaOptions ++= Seq(
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
  .aggregate(imageCopier)
  .enablePlugins(
    PlayScala,
    RiffRaffArtifact,
    JDebPackaging,
    BuildInfoPlugin,
    SystemdPlugin
  )
  .settings(
    Universal / packageName := normalizedName.value,
    maintainer := "Guardian Developer Experience <devx@theguardian.com>",
    Debian / serverLoading := Some(Systemd),
    riffRaffManifestProjectName := s"tools::${name.value}",
    riffRaffPackageType := (Debian / packageBin).value,
    riffRaffArtifactResources ++= Seq(
      (imageCopier / Universal / packageBin).value -> "imagecopier/imagecopier.zip",
      baseDirectory.value / "cdk/cdk.out/AMIgo-CODE.template.json" -> "cloudformation/AMIgo-CODE.template.json",
      baseDirectory.value / "cdk/cdk.out/AMIgo-PROD.template.json" -> "cloudformation/AMIgo-PROD.template.json"
    ),
    // Include the roles dir in the tarball for now
    Universal / mappings ++= (file("roles") ** "*").get.map { f =>
      f.getAbsoluteFile -> f.toString
    },
    buildInfoPackage := "amigo",
    buildInfoKeys := {
      lazy val buildInfo = BuildInfo(baseDirectory.value)

      // so this next one is constant to avoid it always recompiling on dev machines.
      // we only really care about build time on teamcity, when a constant based on when
      // it was loaded is just fine
      lazy val buildTime: String = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .format(ZonedDateTime.now(ZoneId.of("UTC")))

      Seq[BuildInfoKey](
        BuildInfoKey("buildNumber" -> buildInfo.buildIdentifier),
        BuildInfoKey("buildTime" -> buildTime),
        BuildInfoKey("gitCommitId" -> buildInfo.revision)
      )
    },
    buildInfoOptions := Seq(
      BuildInfoOption.Traits("management.BuildInfo"),
      BuildInfoOption.ToJson
    )
  )

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings"
)

val jacksonVersion = "2.15.0"
val circeVersion = "0.14.5"

// These can live in the same codebase, see: https://aws.amazon.com/blogs/developer/aws-sdk-for-java-2-x-released/
val awsV1SdkVersion = "1.12.474"
val awsV2SdkVersion = "2.20.71"

libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "org.scanamo" %% "scanamo" % "1.0.0-M25",
  "com.beachape" %% "enumeratum" % "1.7.2",
  // Pin akka version until Play updates its own akka dependency
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.19", // scala-steward:off
  "com.gu" %% "simple-configuration-ssm" % "1.5.7",
  "com.gu.play-secret-rotation" %% "play-v28" % "0.37",
  "com.gu.play-secret-rotation" %% "aws-parameterstore-sdk-v2" % "0.37",
  "com.gu.play-googleauth" %% "play-v28" % "2.2.7",
  // Pin play-bootstrap because it is tied to the bootstrap version
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B3", // scala-steward:off
  "org.quartz-scheduler" % "quartz" % "2.3.2",
  "com.lihaoyi" %% "fastparse" % "3.0.1",
  "com.amazonaws" % "aws-java-sdk-ec2" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-sns" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-kinesis" % awsV1SdkVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "7.3",
  "software.amazon.awssdk" % "dynamodb" % awsV2SdkVersion,
  "software.amazon.awssdk" % "auth" % awsV2SdkVersion,
  "software.amazon.awssdk" % "regions" % awsV2SdkVersion,
  "com.gu" % "kinesis-logback-appender" % "2.1.1",
  "org.scalatest" %% "scalatest-flatspec" % "3.2.15" % Test,
  "org.scalatest" %% "scalatest-shouldmatchers" % "3.2.15" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,
  "fun.mike" % "diff-match-patch" % "0.0.2",
  "com.gu" %% "anghammarad-client" % "1.2.0"
)
routesGenerator := InjectedRoutesGenerator
routesImport += "models._"

lazy val imageCopier = (project in file("imageCopier"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    scalaVersion := "2.13.10",
    Universal / topLevelDirectory := None,
    Universal / packageName := normalizedName.value,
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-ec2" % awsV1SdkVersion,
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.2",
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )
