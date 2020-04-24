package housekeeping.utils

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{ DescribeInstancesRequest, Filter, Instance, TerminateInstancesRequest }
import models.BakeId
import packer.PackerBuildConfigGenerator

import scala.collection.JavaConversions._

// EC2 methods, specifically related to Packer instances.
class PackerEC2Client(underlying: AmazonEC2, amigoStage: String) {

  private def hasTag(instance: Instance, key: String, value: String): Boolean =
    instance.getTags.exists(tag => tag.getKey == key && tag.getValue == value)

  def getBakeInstance(bakeId: BakeId): Option[Instance] = {
    // Filters here are base on the instance tags that are set in PackerBuildConfigGenerator.
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("tag:AmigoStage", List(amigoStage)),
        new Filter("tag:Stage", List(PackerBuildConfigGenerator.stage)),
        new Filter("tag:Stack", List(PackerBuildConfigGenerator.stack)),
        new Filter("tag:BakeId", List(bakeId.toString)),
        new Filter("instance-state-name", List("running", "stopped"))
      )

    underlying.describeInstances(request)
      .getReservations
      .flatMap(_.getInstances)
      .toList
      .find { instance =>
        hasTag(instance, key = "Stage", value = PackerBuildConfigGenerator.stage) &&
          hasTag(instance, key = "Stack", value = PackerBuildConfigGenerator.stack) &&
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
        new Filter("tag:AmigoStage", List(amigoStage)),
        new Filter("tag:Stage", List(PackerBuildConfigGenerator.stage)),
        new Filter("tag:Stack", List(PackerBuildConfigGenerator.stack)),
        new Filter("tag:Name", List("Packer Builder")),
        new Filter("instance-state-name", List("running", "stopped"))
      )

    underlying.describeInstances(request)
      .getReservations
      .flatMap(_.getInstances)
      .toList
  }
}

