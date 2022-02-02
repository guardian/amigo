package services

import cats.syntax.either._
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{ DescribeImagesRequest, Image }

import scala.collection.JavaConverters._

case class AmiMetadata(architecture: String, debArchitecture: String)

class AmiMetadataLookup(ec2Client: AmazonEC2) {
  val archToDebArch = Map(
    "x86_64" -> "amd64"
  )

  def lookupMetadataFor(ami: String): Either[String, AmiMetadata] = {
    for {
      result <- Either.catchNonFatal {
        ec2Client.describeImages(new DescribeImagesRequest().withImageIds(ami))
      }.leftMap[String](_ => "Call to describe images failed")
      imageDescription <- result.getImages.asScala.headOption.fold[Either[String, Image]](Left(s"No ami with ID $ami found"))(i => Right(i))
      architecture = imageDescription.getArchitecture
      debArchitecture = archToDebArch.getOrElse(architecture, architecture)
    } yield {
      AmiMetadata(architecture, debArchitecture)
    }

  }
}
