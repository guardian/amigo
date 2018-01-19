package com.gu.imageCopier

import com.amazonaws.AmazonClientException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{CopyImageRequest, CreateTagsRequest, CreateTagsResult, Tag}
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
}
