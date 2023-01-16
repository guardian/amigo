package models

/** @param quartzCronExpression
  *   a Quartz cron expression, e.g. "0 0 3 * * ? *" to run at 3am every day See
  *   http://www.quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/crontrigger.html
  */
case class BakeSchedule(quartzCronExpression: String)
