package housekeeping

import cats.syntax.either._
import com.amazonaws.services.ec2.model.Instance
import housekeeping.utils.{BakesRepo, PackerEC2Client}
import models.BakeId
import org.joda.time.DateTime
import org.quartz.{ScheduleBuilder, SimpleScheduleBuilder, Trigger}
import services.Loggable

import scala.collection.JavaConversions._

// TimeOutLongRunningBakes was failing to delete some long running EC2 instances.
// The issue was that the bake corresponding to the long running EC2 instance would have status Failed.
// This means it wouldn't be considered as a long running bake (status not equal to Running)
// and therefore it's corresponding EC2 instance wouldn't be considered for termination.
// To mitigate against this, 'go from the other direction' i.e. query for long running EC2 instances, terminate them
// and then set the status of the corresponding bake to timed out (if it's status is Running).
class DeleteLongRunningEC2Instances(bakesRepo: BakesRepo, packerEC2Client: PackerEC2Client)
  extends HousekeepingJob with Loggable {

  import DeleteLongRunningEC2Instances._

  override val schedule: ScheduleBuilder[_ <: Trigger] = SimpleScheduleBuilder.repeatMinutelyForever(20)

  def getPackerInstancesToTerminate(earliestStartedAt: DateTime): List[Instance] =
    packerEC2Client.getRunningPackerInstances().filter { instance =>
      val launchTime = new DateTime(instance.getLaunchTime)
      launchTime.isBefore(earliestStartedAt)
    }

  def runHouseKeeping(earliestStartedAt: DateTime): Unit = {

    val instancesToTerminate = getPackerInstancesToTerminate(earliestStartedAt)

    if (instancesToTerminate.nonEmpty) {
      log.warn(
        s"instances found to terminate that were launched before $earliestStartedAt" +
          s"${instancesToTerminate.mkString(",")} - terminating these instances"
      )

      instancesToTerminate.foreach { instance =>
        log.info(s"terminating instance ${instance.getInstanceId}")
        packerEC2Client.terminateEC2Instance(instance.getInstanceId) // TODO

        getBakeIdFromInstance(instance) match {
          case None =>
            log.warn(s"unable to get bake id for instance ${instance.getInstanceId}")
          case Some(id) =>
            log.info(s"updating status of $id to timed out")
            // bakesRepo.updateStatusToTimedOutIfRunning(id) // TODO
        }
      }
    }
  }

  override def housekeep(): Unit = runHouseKeeping(DateTime.now.minusHours(1))
}

object DeleteLongRunningEC2Instances {

  def getBakeIdFromInstance(instance: Instance): Option[BakeId] = {
    for {
      raw <- instance.getTags.find(_.getKey == "BakeId")
      id <- BakeId.fromString(raw.getValue).toOption
    } yield id
  }
}