import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

name := "amigo"
version := "1.0-latest"
scalaVersion := "2.13.16"

Universal / javaOptions ++= Seq(
  s"-Dpidfile.path=/dev/null",
  "-J-XX:InitialRAMPercentage=50",
  "-J-XX:MaxRAMPercentage=50",
  "-J-XX:MaxMetaspaceSize=300m",
  "-J-DpackerHome=/opt/packer",
  "-J-Dlogger.resource=logback-PROD.xml",
  s"-J-Dlogs.home=/var/log/${packageName.value}",
  "-J-Xlog:gc*",
  s"-J-Xlog:gc:/var/log/${packageName.value}/gc.log"
)

def env(propName: String): String =
  sys.env.get(propName).filter(_.trim.nonEmpty).getOrElse("DEV")

lazy val root = (project in file("."))
  .aggregate(imageCopier)
  .enablePlugins(
    PlayScala,
    JDebPackaging,
    BuildInfoPlugin,
    SystemdPlugin
  )
  .settings(
    Universal / packageName := normalizedName.value,
    maintainer := "Guardian Developer Experience <devx@theguardian.com>",
    Debian / serverLoading := Some(Systemd),
    // Include the roles dir in the tarball for now
    Universal / mappings ++= (file("roles") ** "*").get.map { f =>
      f.getAbsoluteFile -> f.toString
    },
    buildInfoPackage := "amigo",
    buildInfoKeys := {
      // so this next one is constant to avoid it always recompiling on dev machines.
      // we only really care about build time in CI, when a constant based on when
      // it was loaded is just fine
      lazy val buildTime: String = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .format(ZonedDateTime.now(ZoneId.of("UTC")))

      Seq[BuildInfoKey](
        BuildInfoKey("buildNumber" -> env("BUILD_NUMBER")),
        BuildInfoKey("buildTime" -> buildTime),
        BuildInfoKey("gitCommitId" -> env("GITHUB_SHA"))
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

val jacksonVersion = "2.19.2"
val circeVersion = "0.14.14"

// These can live in the same codebase, see: https://aws.amazon.com/blogs/developer/aws-sdk-for-java-2-x-released/
val awsV1SdkVersion = "1.12.785"
val awsV2SdkVersion = "2.31.70"
val playSecretRotationVersion = "13.2.0"

libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "org.scanamo" %% "scanamo" % "3.0.0",
  "com.beachape" %% "enumeratum" % "1.9.0",
  "com.gu" %% "simple-configuration-ssm" % "6.0.0",
  "com.gu.play-secret-rotation" %% "play-v30" % playSecretRotationVersion,
  "com.gu.play-secret-rotation" %% "aws-parameterstore-sdk-v2" % playSecretRotationVersion,
  "com.gu.play-googleauth" %% "play-v30" % "20.1.0",
  // Pin play-bootstrap because it is tied to the bootstrap version
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B3", // scala-steward:off
  "org.quartz-scheduler" % "quartz" % "2.5.0",
  "com.lihaoyi" %% "fastparse" % "3.1.1",
  "com.amazonaws" % "aws-java-sdk-ec2" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-sns" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsV1SdkVersion,
  "com.amazonaws" % "aws-java-sdk-kinesis" % awsV1SdkVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "8.1",
  // Transient dependency of Play. No newer version of Play with this vulnerability fixed.
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "software.amazon.awssdk" % "dynamodb" % awsV2SdkVersion,
  "software.amazon.awssdk" % "sqs" % awsV2SdkVersion,
  "software.amazon.awssdk" % "auth" % awsV2SdkVersion,
  "software.amazon.awssdk" % "regions" % awsV2SdkVersion,
  "org.scalatest" %% "scalatest-flatspec" % "3.2.19" % Test,
  "org.scalatest" %% "scalatest-shouldmatchers" % "3.2.19" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,
  "fun.mike" % "diff-match-patch" % "0.0.2",
  "com.gu" %% "anghammarad-client" % "5.0.0"
)
routesGenerator := InjectedRoutesGenerator
routesImport += "models._"

lazy val imageCopier = (project in file("imageCopier"))
  .enablePlugins(
    JavaAppPackaging,

    // Though this project doesn't use JDebPackaging, it is used in the root project and needed here to prevent the error: java.io.IOException: Cannot run program "fakeroot"
    JDebPackaging
  )
  .settings(
    scalaVersion := "2.13.16",
    Universal / topLevelDirectory := None,
    Universal / packageName := normalizedName.value,
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "ec2" % awsV2SdkVersion,
      "com.amazonaws" % "aws-lambda-java-core" % "1.3.0",
      "com.amazonaws" % "aws-lambda-java-events" % "3.16.1",
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )
