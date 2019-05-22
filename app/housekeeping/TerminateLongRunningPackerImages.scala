package housekeeping

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Filter, Instance, TerminateInstancesRequest}
import data.{Bakes, Dynamo}
import models.{Bake, BakeId, BakeStatus}
import org.joda.time.DateTime
import org.quartz.{ScheduleBuilder, SimpleScheduleBuilder, Trigger}
import services.Loggable

import scala.collection.JavaConversions._
import scala.concurrent.duration._

// TODO: if we didn't care about a different status - TimeOut - (maybe?) we could just terminate the instance
// and the PackerProcessMonitor would report the task as failed and status update would be handled using current mechanism.

// This house keeping job is to mitigate against Amigo bakes that have failed, but have not reported as such.
// As a result of this, the EC2 instance used for the bake would not be terminated, incurring unnecessary costs.
// The solution is to terminate all EC2 instances with Stack amigo-packer that were launched over an hour ago,
// and update the status (in the database) of Running bakes that were launched over an hour ago to status TimedOut.
class TerminateLongRunningPackerImages(stage: String, ec2Client: AmazonEC2)(implicit dynamo: Dynamo)
  extends HousekeepingJob with Loggable {

  // Line in the sand: no bake should take more than an hour.
  private val timeOut = 1.hour

  // By running every 20 minutes, the maximum lifetime of an EC2 instance used to bake an Amigo image will 1 hour and 20 minutes.
  // Based on current bakes in the PROD bake table (~500) and the provisioned throughput of said table (1),
  // the minimum period for the schedule is ~10 minutes = 500 / 60.
  // Increase min period to 20 minutes to allow for increase of records in bakes table.
  override val schedule: ScheduleBuilder[_ <: Trigger] = SimpleScheduleBuilder.repeatMinutelyForever(20)

  private def isExpired(dateTime: DateTime): Boolean =
   dateTime.isBefore(DateTime.now.minusSeconds(timeOut.toSeconds.toInt))

  private def shouldDeleteBake(bake: Bake.DbModel): Boolean =
    bake.status == BakeStatus.Running && isExpired(bake.startedAt)

  private def updateStatusToTimedOut(bake: Bake.DbModel): Unit =
    Bakes.updateStatus(BakeId(bake.recipeId, bake.buildNumber), BakeStatus.TimedOut)

  // TODO: test this works
  private def packerInstances(): List[Instance] = {
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter(s"tag:Stage=$stage"),
        new Filter("tag:Stack=amigo-packer")
      )
    val result = ec2Client.describeInstances(request)
    result.getReservations
      .flatMap(reservation => reservation.getInstances)
      .toList
  }

  private def shouldTerminatePackerInstance(instance: Instance): Boolean = {
    val launchTime = new DateTime(instance.getLaunchTime)
    isExpired(launchTime)
  }

  private def terminateEC2Instance(instanceId: String): Unit = {
    val request = new TerminateInstancesRequest().withInstanceIds(instanceId)
    // val result = ec2Client.terminateInstances(request)
    // TODO: await until instances have been terminated?
  }

  override def housekeep(): Unit = {

    // TODO: does ordering of actions matter here?

    log.info("scanning for long running bakes to mark as timed out")

    val bakesToDelete = Bakes.scanForAll().filter(shouldDeleteBake)

    log.info(s"${bakesToDelete.size} long running bake(s) found for marking as timed out")

    bakesToDelete.foreach { bake =>
      log.info(s"marking long running bake $bake as timed out")
      updateStatusToTimedOut(bake)
    }

    log.info("getting old packer instances for termination")

    val instances = packerInstances().filter(shouldTerminatePackerInstance)

    log.info(s"${instances.size} old packer instance(s) found for termination")

    instances.foreach { instance =>
      val instanceId = instance.getInstanceId
      log.info(s"deleting packer instance $instanceId")
      terminateEC2Instance(instanceId)
    }
  }
}
