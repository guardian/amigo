package housekeeping

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Filter, Instance, TerminateInstancesRequest}
import data.{Bakes, Dynamo}
import models.{Bake, BakeId, BakeStatus}
import org.joda.time.DateTime
import org.quartz.{ScheduleBuilder, SimpleScheduleBuilder, Trigger}
import packer.PackerBuildConfigGenerator
import services.Loggable

import scala.collection.JavaConversions._
import scala.concurrent.duration._

// This house keeping job is to mitigate against Amigo bakes that have failed, but have not reported as such.
// As a result of this, the EC2 instance used for the bake would not be terminated, incurring unnecessary costs.
// The solution is to terminate all EC2 instances with Stack amigo-packer that were launched over an hour ago,
// and update the status (in the database) of Running bakes that were launched over an hour ago to status TimedOut.
class TerminateLongRunningBakes(stage: String, ec2Client: AmazonEC2)(implicit dynamo: Dynamo)
  extends HousekeepingJob with Loggable {

  // Line in the sand: no bake should take more than an hour.
  private val timeOut = 1.hour

  // By running every 20 minutes, the maximum lifetime of an EC2 instance used to bake an Amigo image will 1 hour and 20 minutes.
  // Based on current bakes in the PROD bake table (~500) and the provisioned throughput of said table (1),
  // the minimum period for the schedule is ~10 minutes = 500 / 60.
  // Increase min period to 20 minutes to allow for increase of records in bakes table.
  override val schedule: ScheduleBuilder[_ <: Trigger] = SimpleScheduleBuilder.repeatMinutelyForever(20)

  private def isOverruning(dateTime: DateTime): Boolean =
   dateTime.isBefore(DateTime.now.minusSeconds(timeOut.toSeconds.toInt))

  private def shouldTimeoutBake(bake: Bake.DbModel): Boolean =
    bake.status == BakeStatus.Running && isOverruning(bake.startedAt)

  private def updateStatusToTimedOut(bake: Bake.DbModel): Unit =
    Bakes.updateStatus(BakeId(bake.recipeId, bake.buildNumber), BakeStatus.TimedOut)

  private def hasTag(instance: Instance, key: String, value: String): Boolean =
    instance.getTags.exists(tag => tag.getKey == key && tag.getValue == value)

  private def getPackerInstance(bakeId: BakeId): Option[Instance] = {
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("tag:Stage", List(PackerBuildConfigGenerator.stage)),
        new Filter("tag:Stack", List(PackerBuildConfigGenerator.stack)),
        new Filter("tag:BakeId", List(bakeId.toString)),
        new Filter("instance-state-name", List("running", "stopped"))
      )

    ec2Client.describeInstances(request)
      .getReservations
      .flatMap(_.getInstances)
      .toList
      .find { instance =>
        hasTag(instance, key = "Stage", value = PackerBuildConfigGenerator.stage) &&
        hasTag(instance, key = "Stack", value = PackerBuildConfigGenerator.stack) &&
        hasTag(instance, key = "BakeId", value = bakeId.toString)
      }
  }

  // Terminate instead of stop, since we don't want to restart instance.
  // Termination also removes associated security groups.
  private def terminateEC2Instance(instanceId: String): Unit = {
    val request = new TerminateInstancesRequest().withInstanceIds(instanceId)
    ec2Client.terminateInstances(request)
  }

  override def housekeep(): Unit = {

    log.info("scanning for long running bakes to mark as timed out")

    val bakesToTimeout = Bakes.scanForAll().filter(shouldTimeoutBake)

    log.info(s"${bakesToTimeout.size} long running bake(s) found for marking as timed out")

    bakesToTimeout.foreach { bake =>

      getPackerInstance(bake.bakeId) match {
        case None =>
          log.warn(s"unable to find packer instance associated with long running bake ${bake.bakeId}")

        case Some(instance) =>
          val instanceId = instance.getInstanceId

          log.info(s"terminating instance $instanceId associated with long running bake ${bake.bakeId}")
          terminateEC2Instance(instanceId)

          log.info(s"marking long running bake $bake as timed out")
          updateStatusToTimedOut(bake)
      }
    }
  }
}
