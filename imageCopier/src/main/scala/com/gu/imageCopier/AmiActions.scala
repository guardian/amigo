package com.gu.imageCopier

import com.amazonaws.AmazonClientException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model._
import com.gu.imageCopier.attempt.{Attempt, AwsSdkFailure}

import scala.collection.JavaConverters._

object AmiActions {
  def copyAmi(amiEvent: AmiEvent, kmsArn: String)(implicit ec2Client: AmazonEC2): Attempt[String] = {
    Attempt.catchNonFatal {
      val request = new CopyImageRequest()
        .withSourceImageId(amiEvent.sourceAmi)
        .withSourceRegion(amiEvent.sourceRegion)
        .withKmsKeyId(kmsArn)
        .withEncrypted(true)
        .withName(amiEvent.name)
        .withDescription(amiEvent.description)
      println(s"Copying AMI with request $request")
      val result = ec2Client.copyImage(request)
      println(s"New AMI is ${result.getImageId}")
      result.getImageId
    } {
      case ace:AmazonClientException => AwsSdkFailure(ace)
    }
  }

  def tagAmi(amiEvent: AmiEvent, encryptedTagValue: String, newAmiId: String)(implicit ec2Client: AmazonEC2): Attempt[CreateTagsResult] = {
    Attempt.catchNonFatal {
      val tags = amiEvent.tags ++ Map("Encrypted" -> encryptedTagValue, "CopiedFromAMI" -> amiEvent.sourceAmi)
      val awsTags = tags.map { case (k, v) =>
          new Tag(k, v)
      }.toList
      val request = new CreateTagsRequest()
        .withResources(newAmiId)
        .withTags(awsTags.asJava)
      println(s"Creating tags with request $request")
      val result = ec2Client.createTags(request)
      println(s"Succeeded")
      result
    } {
      case ace:AmazonClientException => AwsSdkFailure(ace)
    }
  }

  def getImagesAndEbsSnapshots(amis: List[Ami])(implicit ec2Client: AmazonEC2): Attempt[List[(String, List[String])]] = {
    Attempt.catchNonFatal {
      val request = new DescribeImagesRequest()
        .withImageIds(amis.map(_.id).asJava)
      println(s"Getting list of images and EBS snapshots with request $request")
      val result = ec2Client.describeImages(request)
      println(s"Raw result: $result")
      val ids = result.getImages.asScala.map { image =>
        image.getImageId -> image.getBlockDeviceMappings.asScala.toList.flatMap(mapping => Option(mapping.getEbs).map(_.getSnapshotId))
      }
      println(s"List of AMIs and snapshots: $ids")
      ids.toList
    } {
      case ace:AmazonClientException => AwsSdkFailure(ace)
    }
  }

  def deregisterAmi(amiId: String)(implicit ec2Client: AmazonEC2): Attempt[String] = {
    Attempt.catchNonFatal {
      val request = new DeregisterImageRequest()
        .withImageId(amiId)
      println(s"Deregistering AMI with request $request")
      val result = ec2Client.deregisterImage(request)
      println(s"Deregistered")
      amiId
    } {
      case ace:AmazonClientException => AwsSdkFailure(ace)
    }
  }

  def deleteSnapshot(snapshotId: String)(implicit ec2Client: AmazonEC2): Attempt[String] = {
    Attempt.catchNonFatal {
      val request = new DeleteSnapshotRequest()
        .withSnapshotId(snapshotId)
      println(s"Deleting snapshot with request $request")
      val result = ec2Client.deleteSnapshot(request)
      println(s"Deleted snapshot")
      snapshotId
    } {
      case ace:AmazonClientException => AwsSdkFailure(ace)
    }
  }
}
