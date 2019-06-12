package housekeeping

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Filter, Instance, TerminateInstancesRequest}
import data.{Bakes, Dynamo}
import housekeeping.TimeOutLongRunningBakes.{BakesRepo, PackerEC2Client}
import models.{Bake, BakeId, BakeStatus}
import org.joda.time.DateTime
import org.quartz.{ScheduleBuilder, SimpleScheduleBuilder, Trigger}
import packer.PackerBuildConfigGenerator
import services.Loggable

import scala.collection.JavaConversions._

// This house keeping job is to mitigate against Amigo bakes that have failed, but have not reported as such.
// As a result of this, the EC2 instance used for the bake would not be terminated, incurring unnecessary costs.
// The solution is to update the status (in the database) of running bakes that were launched over an hour ago to TimedOut
// and terminate the EC2 instance associated with them respectively.
class TimeOutLongRunningBakes private[housekeeping](stage: String, bakesRepo: BakesRepo, packerEC2Client: PackerEC2Client)(implicit dynamo: Dynamo)
  extends HousekeepingJob with Loggable {

  // By running every 20 minutes, the maximum lifetime of an EC2 instance used to bake an Amigo image will 1 hour and 20 minutes.
  // Based on current bakes in the PROD bake table (~500) and the provisioned throughput of said table (1),
  // the minimum period for the schedule is ~10 minutes = 500 / 60.
  // Increase min period to 20 minutes to allow for increase of records in bakes table.
  override val schedule: ScheduleBuilder[_ <: Trigger] = SimpleScheduleBuilder.repeatMinutelyForever(20)

  def getBakesToTimeOut(earliestStartedAt: DateTime): List[Bake.DbModel] =
    bakesRepo.getBakes.filter { bake =>
      TimeOutLongRunningBakes.shouldTimeOutBake(bake, earliestStartedAt)
    }

  def runHouseKeeping(earliestStartedAt: DateTime): Unit = {

    log.info("scanning for long running bakes to mark as timed out")

    val bakesToTimeout = getBakesToTimeOut(earliestStartedAt)

    log.info(s"${bakesToTimeout.size} long running bake(s) found for marking as timed out")

    bakesToTimeout.foreach { bake =>

      packerEC2Client.getInstance(bake.bakeId) match {
        case None =>
          log.warn(
            s"unable to find instance associated with long running bake ${bake.bakeId} - " +
            "assuming instance was deleted manually"
          )

        case Some(instance) =>
          val instanceId = instance.getInstanceId
          log.info(s"terminating instance $instanceId associated with long running bake ${bake.bakeId}")
          packerEC2Client.terminateEC2Instance(instanceId)
      }

      // Update the status to TimedOut, even if the respective EC2 instance can't be found.
      // This is to handle cases where e.g. the instance was deleted manually.
      log.info(s"marking long running bake $bake as timed out")
      bakesRepo.updateStatusToTimedOut(bake)
    }
  }

  override def housekeep(): Unit = runHouseKeeping(earliestStartedAt = DateTime.now.minusHours(1))
}

object TimeOutLongRunningBakes {

  def apply(stage: String, ec2Client: AmazonEC2)(implicit dynamo: Dynamo): TimeOutLongRunningBakes =
    new TimeOutLongRunningBakes(stage, new BakesRepo, new PackerEC2Client(ec2Client))

  def shouldTimeOutBake(bake: Bake.DbModel, earliestStartedAt: DateTime): Boolean =
    bake.status == BakeStatus.Running && bake.startedAt.isBefore(earliestStartedAt)

  // Utility classes to help implement the TerminateLongRunningBakes housekeeping task.
  // Injected in as dependencies to facilitate unit tests.

  class BakesRepo(implicit dynamo: Dynamo) {

    def getBakes: List[Bake.DbModel] = Bakes.scanForAll()

    def updateStatusToTimedOut(bake: Bake.DbModel): Unit =
      Bakes.updateStatus(BakeId(bake.recipeId, bake.buildNumber), BakeStatus.TimedOut)
  }

  class PackerEC2Client(underlying: AmazonEC2) {

    private def hasTag(instance: Instance, key: String, value: String): Boolean =
      instance.getTags.exists(tag => tag.getKey == key && tag.getValue == value)

    def getInstance(bakeId: BakeId): Option[Instance] = {
      val request = new DescribeInstancesRequest()
        .withFilters(
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
  }
}