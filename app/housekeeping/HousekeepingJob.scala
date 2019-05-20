package housekeeping

import attempt.Attempt
import org.quartz._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

trait HousekeepingJob {
  val schedule: ScheduleBuilder[_ <: Trigger]
  val timeout: Duration

  val name: String = getClass.getName

  val jobKey = new JobKey(name)
  val triggerKey = new TriggerKey(name)

  def housekeep(executionContext: ExecutionContext): Attempt[Unit]
}
