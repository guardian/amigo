package schedule

import models.RecipeId
import org.quartz.{JobExecutionContext, JobDataMap, Job}
import schedule.BakeScheduler.JobDataKeys

/** Quartz job wrapper for [[schedule.ScheduledBakeRunner]] */
class BakeJob extends Job {

  private def getAs[T](key: String)(implicit jobDataMap: JobDataMap): T =
    jobDataMap.get(key).asInstanceOf[T]

  override def execute(context: JobExecutionContext): Unit = {
    implicit val jobDataMap = context.getJobDetail.getJobDataMap

    val recipeId = getAs[RecipeId](JobDataKeys.RecipeId)
    val runner = getAs[ScheduledBakeRunner](JobDataKeys.Runner)

    runner.bake(
      recipeId,
      bakeNumber = None
    ) // we'll calculate the bake number later
  }

}
