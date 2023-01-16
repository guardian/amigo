package housekeeping.utils

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{
  DescribeInstancesRequest,
  Filter,
  Instance,
  TerminateInstancesRequest
}
import models.BakeId
import packer.PackerBuildConfigGenerator

import scala.jdk.CollectionConverters._

// EC2 methods, specifically related to Packer instances.
class PackerEC2Client(underlying: AmazonEC2, amigoStage: String) {

  private def hasTag(instance: Instance, key: String, value: String): Boolean =
    instance.getTags.asScala.exists(tag =>
      tag.getKey == key && tag.getValue == value
    )

  def getBakeInstance(bakeId: BakeId): Option[Instance] = {
    // Filters here are base on the instance tags that are set in PackerBuildConfigGenerator.
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("tag:AmigoStage", List(amigoStage).asJava),
        new Filter("tag:Stage", List(PackerBuildConfigGenerator.stage).asJava),
        new Filter("tag:Stack", List(PackerBuildConfigGenerator.stack).asJava),
        new Filter("tag:BakeId", List(bakeId.toString).asJava),
        new Filter("instance-state-name", List("running", "stopped").asJava)
      )

    underlying
      .describeInstances(request)
      .getReservations
      .asScala
      .flatMap(_.getInstances.asScala)
      .find { instance =>
        hasTag(
          instance,
          key = "Stage",
          value = PackerBuildConfigGenerator.stage
        ) &&
        hasTag(
          instance,
          key = "Stack",
          value = PackerBuildConfigGenerator.stack
        ) &&
        hasTag(instance, key = "BakeId", value = bakeId.toString)
      }
  }

  def terminateEC2Instance(instanceId: String): Unit = {
    val request = new TerminateInstancesRequest().withInstanceIds(instanceId)
    underlying.terminateInstances(request)
  }

  def getRunningPackerInstances(): List[Instance] = {
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("tag:AmigoStage", List(amigoStage).asJava),
        new Filter("tag:Stage", List(PackerBuildConfigGenerator.stage).asJava),
        new Filter("tag:Stack", List(PackerBuildConfigGenerator.stack).asJava),
        new Filter("tag:Name", List("Packer Builder").asJava),
        new Filter("instance-state-name", List("running", "stopped").asJava)
      )

    underlying
      .describeInstances(request)
      .getReservations
      .asScala
      .flatMap(_.getInstances.asScala)
      .toList
  }
}
