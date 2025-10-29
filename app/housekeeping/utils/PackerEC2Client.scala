package housekeeping.utils

import models.BakeId
import packer.PackerBuildConfigGenerator
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{
  DescribeInstancesRequest,
  Filter,
  Instance,
  TerminateInstancesRequest
}

import scala.jdk.CollectionConverters._

// EC2 methods, specifically related to Packer instances.
class PackerEC2Client(underlying: Ec2Client, amigoStage: String) {

  private def hasTag(instance: Instance, key: String, value: String): Boolean =
    instance
      .tags()
      .asScala
      .exists(tag => tag.key() == key && tag.value() == value)

  private def commonFilters(amigoStage: String): Seq[Filter] = Seq(
    Filter.builder.name("tag:AmigoStage").values(amigoStage).build(),
    Filter.builder
      .name("tag:Stage")
      .values(PackerBuildConfigGenerator.stage)
      .build(),
    Filter.builder
      .name("tag:Stack")
      .values(PackerBuildConfigGenerator.stack)
      .build(),
    Filter.builder
      .name("instance-state-name")
      .values("running", "stopped")
      .build()
  )

  def getBakeInstance(bakeId: BakeId): Option[Instance] = {
    // Filters here are base on the instance tags that are set in PackerBuildConfigGenerator.
    val filters = commonFilters(amigoStage) ++ Seq(
      Filter.builder().name("tag:BakeId").values(bakeId.toString).build()
    )
    val request = DescribeInstancesRequest
      .builder()
      .filters(
        filters: _*
      )
      .build()

    underlying
      .describeInstances(request)
      .reservations()
      .asScala
      .flatMap(_.instances().asScala)
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
    val request = TerminateInstancesRequest
      .builder()
      .instanceIds(instanceId)
      .build()
    underlying.terminateInstances(request)
  }

  def getRunningPackerInstances(): List[Instance] = {
    val filters = commonFilters(amigoStage) ++ Seq(
      Filter.builder().name("tag:Name").values("Packer Builder").build()
    )
    val request = DescribeInstancesRequest
      .builder()
      .filters(
        filters: _*
      )
      .build()

    underlying
      .describeInstances(request)
      .reservations()
      .asScala
      .flatMap(_.instances().asScala)
      .toList
  }
}
