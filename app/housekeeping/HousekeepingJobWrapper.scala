package housekeeping

import org.quartz.{ DisallowConcurrentExecution, Job, JobDataMap, JobExecutionContext }
import services.Loggable

import scala.concurrent.{ Await, ExecutionContext }

/** Quartz job wrapper for [[schedule.ScheduledBakeRunner]] */
@DisallowConcurrentExecution
class HousekeepingJobWrapper extends Job with Loggable {

  private def getAs[T](key: String)(implicit jobDataMap: JobDataMap): T = jobDataMap.get(key).asInstanceOf[T]

  override def execute(context: JobExecutionContext): Unit = {
    implicit val jobDataMap: JobDataMap = context.getJobDetail.getJobDataMap

    val housekeepingJob = getAs[HousekeepingJob](HousekeepingScheduler.HousekeepingJobInstance)
    implicit val ec = getAs[ExecutionContext](HousekeepingScheduler.HousekeepingJobInstance)

    val result = housekeepingJob.housekeep(ec).fold({
      failure => log.warn(s"${housekeepingJob.getClass.getSimpleName} failed with $failure")
    }, identity)
    Await.ready(result, housekeepingJob.timeout)
  }

}

