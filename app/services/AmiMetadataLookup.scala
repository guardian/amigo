package services

import cats.syntax.either._
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{DescribeImagesRequest, Image}

import scala.jdk.CollectionConverters._

case class AmiMetadata(architecture: String, debArchitecture: String)

class AmiMetadataLookup(ec2Client: Ec2Client) {
  val archToDebArch = Map("x86_64" -> "amd64")

  def lookupMetadataFor(ami: String): Either[String, AmiMetadata] = {
    for {
      result <- Either
        .catchNonFatal {
          ec2Client.describeImages(
            DescribeImagesRequest
              .builder()
              .imageIds(ami)
              .build()
          )
        }
        .leftMap[String](_ => "Call to describe images failed")
      imageDescription <- result
        .images()
        .asScala
        .headOption
        .fold[Either[String, Image]](Left(s"No ami with ID $ami found"))(i =>
          Right(i)
        )
      architecture = imageDescription.architectureAsString()
      debArchitecture = archToDebArch.getOrElse(architecture, architecture)
    } yield {
      AmiMetadata(architecture, debArchitecture)
    }

  }
}
