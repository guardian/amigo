package com.gu.imageCopier

import software.amazon.awssdk.core.exception.SdkServiceException
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model._
import com.gu.imageCopier.attempt.{Attempt, AwsSdkFailure}
import scala.jdk.CollectionConverters._

object AmiActions {
  def copyAmi(amiEvent: AmiEvent, kmsArn: String)(implicit
      ec2Client: Ec2Client
  ): Attempt[String] = {
    Attempt.catchNonFatal {
      val request = CopyImageRequest.builder
        .sourceImageId(amiEvent.sourceAmi)
        .sourceRegion(amiEvent.sourceRegion)
        .kmsKeyId(kmsArn)
        .encrypted(true)
        .name(amiEvent.name)
        .description(amiEvent.description)
        .build()
      println(s"Copying AMI with request $request")
      val response = ec2Client.copyImage(request)
      println(s"New AMI is ${response.imageId}")
      response.imageId
    } { case ace: SdkServiceException =>
      AwsSdkFailure(ace)
    }
  }

  def tagAmi(amiEvent: AmiEvent, encryptedTagValue: String, newAmiId: String)(
      implicit ec2Client: Ec2Client
  ): Attempt[CreateTagsResponse] = {
    Attempt.catchNonFatal {
      val tags = amiEvent.tags ++ Map(
        "Encrypted" -> encryptedTagValue,
        "CopiedFromAMI" -> amiEvent.sourceAmi
      )
      val awsTags = tags.map { case (k, v) =>
        Tag.builder.key(k).value(v).build()
      }.toList
      val request = CreateTagsRequest.builder
        .resources(newAmiId)
        .tags(awsTags.asJava)
        .build()
      println(s"Creating tags with request $request")
      val response = ec2Client.createTags(request)
      println(s"Succeeded")
      response
    } { case ace: SdkServiceException =>
      AwsSdkFailure(ace)
    }
  }

  def getImagesAndEbsSnapshots(
      amis: List[Ami]
  )(implicit ec2Client: Ec2Client): Attempt[List[(String, List[String])]] = {
    if (amis.nonEmpty) {
      Attempt.catchNonFatal {
        val request = DescribeImagesRequest.builder
          .imageIds(amis.map(_.id).asJava)
          .build()
        println(
          s"Getting list of images and EBS snapshots with request $request"
        )
        val response = ec2Client.describeImages(request)
        println(s"Raw result: $response")
        val ids = response.images.asScala.map { image =>
          image.imageId -> image.blockDeviceMappings.asScala.toList
            .flatMap(mapping => Option(mapping.ebs).map(_.snapshotId))
        }
        println(s"List of AMIs and snapshots: $ids")
        ids.toList
      } { case ace: SdkServiceException =>
        AwsSdkFailure(ace)
      }
    } else {
      Attempt.Right(Nil)
    }
  }

  def deregisterAmi(
      amiId: String
  )(implicit ec2Client: Ec2Client): Attempt[String] = {
    Attempt.catchNonFatal {
      val request = DeregisterImageRequest.builder
        .imageId(amiId)
        .build()
      println(s"Deregistering AMI with request $request")
      val response = ec2Client.deregisterImage(request)
      println(s"Deregistered")
      amiId
    } { case ace: SdkServiceException =>
      AwsSdkFailure(ace)
    }
  }

  def deleteSnapshot(
      snapshotId: String
  )(implicit ec2Client: Ec2Client): Attempt[String] = {
    Attempt.catchNonFatal {
      val request = DeleteSnapshotRequest.builder
        .snapshotId(snapshotId)
        .build
      println(s"Deleting snapshot with request $request")
      val response = ec2Client.deleteSnapshot(request)
      println(s"Deleted snapshot")
      snapshotId
    } { case ace: SdkServiceException =>
      AwsSdkFailure(ace)
    }
  }
}
