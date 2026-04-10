import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

name := "amigo"
version := "1.0-latest"
scalaVersion := "2.13.18"

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

val jacksonV2Version = "2.21.2"
val circeVersion = "0.14.15"

val awsV2SdkVersion = "2.42.23"
val playSecretRotationVersion = "17.0.2"

/*
 * To test whether any of these entries are redundant:
 * 1. Comment it out
 * 2. Run `sbt dependencyList`
 * 3. If no earlier version appears in the dependency list, the entry can be removed.
 */
val safeTransitiveDependencies = {
  val jacksonV3Version = "3.1.1"
  Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonV2Version,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonV2Version,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonV2Version,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonV2Version,
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV2Version,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV2Version,
    "tools.jackson.core" % "jackson-core" % jacksonV3Version,
    "tools.jackson.core" % "jackson-databind" % jacksonV3Version,
    "ch.qos.logback" % "logback-classic" % "1.5.32"
  )
}

libraryDependencies ++= Seq(
  ws,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonV2Version,
  "org.scanamo" %% "scanamo" % "6.0.0",
  "com.beachape" %% "enumeratum" % "1.9.6",
  "com.gu" %% "simple-configuration-ssm" % "9.2.1",
  "com.gu.play-secret-rotation" %% "play-v30" % playSecretRotationVersion,
  "com.gu.play-secret-rotation" %% "aws-parameterstore-sdk-v2" % playSecretRotationVersion,
  "com.gu.play-googleauth" %% "play-v30" % "35.0.0",
  // Pin play-bootstrap because it is tied to the bootstrap version
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B3", // scala-steward:off
  "org.quartz-scheduler" % "quartz" % "2.5.2",
  "com.lihaoyi" %% "fastparse" % "3.1.1",
  "joda-time" % "joda-time" % "2.14.1",
  "software.amazon.awssdk" % "ec2" % awsV2SdkVersion,
  "software.amazon.awssdk" % "sns" % awsV2SdkVersion,
  "software.amazon.awssdk" % "s3" % awsV2SdkVersion,
  "software.amazon.awssdk" % "sts" % awsV2SdkVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "9.0",
  "software.amazon.awssdk" % "dynamodb" % awsV2SdkVersion,
  "software.amazon.awssdk" % "auth" % awsV2SdkVersion,
  "software.amazon.awssdk" % "regions" % awsV2SdkVersion,
  "org.scalatest" %% "scalatest-flatspec" % "3.2.19" % Test,
  "org.scalatest" %% "scalatest-shouldmatchers" % "3.2.19" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,
  "org.mockito" % "mockito-inline" % "5.2.0" % Test,
  "fun.mike" % "diff-match-patch" % "0.0.2",
  "com.gu" %% "anghammarad-client" % "6.0.0"
) ++ safeTransitiveDependencies
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
      "com.amazonaws" % "aws-lambda-java-core" % "1.4.0",
      "com.amazonaws" % "aws-lambda-java-events" % "3.16.1",
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )
