package housekeeping

import org.quartz.{JobDataMap, Scheduler}
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._

class HousekeepingScheduler(
    scheduler: Scheduler,
    housekeepingJobs: List[HousekeepingJob]
) {

  def initialise(): Unit = {
    housekeepingJobs.foreach { housekeepingJob =>
      val jobDetail = newJob(classOf[HousekeepingJobWrapper])
        .withIdentity(housekeepingJob.jobKey)
        .usingJobData(buildJobDataMap(housekeepingJob))
        .build()
      val trigger = newTrigger()
        .withIdentity(housekeepingJob.triggerKey)
        .withSchedule(housekeepingJob.schedule)
        .build()
      scheduler.scheduleJob(jobDetail, trigger)
    }
  }

  private def buildJobDataMap(housekeepingJob: HousekeepingJob): JobDataMap = {
    val map = new JobDataMap()
    map.put(HousekeepingScheduler.HousekeepingJobInstance, housekeepingJob)
    map
  }
}

object HousekeepingScheduler {
  val HousekeepingJobInstance = "HousekeepingJobInstance"
}
