package housekeeping

import org.quartz._

trait HousekeepingJob {
  val schedule: ScheduleBuilder[_ <: Trigger]

  val name: String = getClass.getName

  val jobKey = new JobKey(name)
  val triggerKey = new TriggerKey(name)

  def housekeep(): Unit
}
