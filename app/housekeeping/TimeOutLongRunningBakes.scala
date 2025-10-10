package housekeeping

import housekeeping.utils.{BakesRepo, PackerEC2Client}
import models.{Bake, BakeStatus}
import org.joda.time.DateTime
import org.quartz.{ScheduleBuilder, SimpleScheduleBuilder, Trigger}
import services.Loggable

// This house keeping job is to mitigate against Amigo bakes that have failed, but have not reported as such.
// As a result of this, the EC2 instance used for the bake would not be terminated, incurring unnecessary costs.
// The solution is to update the status (in the database) of running bakes that were launched over 2 hours ago to TimedOut
// and terminate the EC2 instance associated with them respectively.
class TimeOutLongRunningBakes(
    bakesRepo: BakesRepo,
    packerEC2Client: PackerEC2Client
) extends HousekeepingJob
    with Loggable {

  override val schedule: ScheduleBuilder[_ <: Trigger] =
    SimpleScheduleBuilder.repeatMinutelyForever(20)

  def getBakesToTimeOut(earliestStartedAt: DateTime): List[Bake.DbModel] =
    bakesRepo.getBakes.filter { bake =>
      TimeOutLongRunningBakes.shouldTimeOutBake(bake, earliestStartedAt)
    }

  def runHouseKeeping(earliestStartedAt: DateTime): Unit = {

    log.info("scanning for long running bakes to mark as timed out")

    val bakesToTimeout = getBakesToTimeOut(earliestStartedAt)

    log.info(
      s"${bakesToTimeout.size} long running bake(s) found for marking as timed out"
    )

    bakesToTimeout.foreach { bake =>
      packerEC2Client.getBakeInstance(bake.bakeId) match {
        case None =>
          log.warn(
            s"unable to find instance associated with long running bake ${bake.bakeId}"
          )

        case Some(instance) =>
          val instanceId = instance.instanceId()
          log.info(
            s"terminating instance $instanceId associated with long running bake ${bake.bakeId}"
          )
          packerEC2Client.terminateEC2Instance(instanceId)
      }

      // Update the status to TimedOut, even if the respective EC2 instance can't be found.
      // This is to handle cases where e.g. the instance was deleted manually.
      log.info(s"marking long running bake $bake as timed out")
      bakesRepo.updateStatusToTimedOutIfRunning(bake.bakeId)
    }
  }

  override def housekeep(): Unit =
    runHouseKeeping(earliestStartedAt = DateTime.now.minusHours(2))
}

object TimeOutLongRunningBakes {

  def shouldTimeOutBake(
      bake: Bake.DbModel,
      earliestStartedAt: DateTime
  ): Boolean =
    bake.status == BakeStatus.Running && bake.startedAt.isBefore(
      earliestStartedAt
    )
}
