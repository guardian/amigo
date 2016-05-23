package schedule

import models.{ BakeSchedule, Recipe, RecipeId }
import org.quartz.CronScheduleBuilder._
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{ TriggerKey, JobKey, JobDataMap }
import play.api.Logger

class BakeScheduler(scheduledBakeRunner: ScheduledBakeRunner) {

  private val scheduler = StdSchedulerFactory.getDefaultScheduler()

  def initialise(recipes: Iterable[Recipe]): Unit = {
    recipes.flatMap(r => r.bakeSchedule.map(s => (r.id, s))).foreach {
      case (recipeId, bakeSchedule) => scheduleBake(recipeId, bakeSchedule)
    }
  }

  def reschedule(recipe: Recipe): Unit = {
    // Delete any job and trigger that we may have previously created
    scheduler.deleteJob(jobKey(recipe.id))

    // If the recipe has a bake schedule, schedule it
    recipe.bakeSchedule.foreach { bakeSchedule =>
      scheduleBake(recipe.id, bakeSchedule)
    }
  }

  private def scheduleBake(recipeId: RecipeId, bakeSchedule: BakeSchedule): Unit = {
    val jobDetail = newJob(classOf[BakeJob])
      .withIdentity(jobKey(recipeId))
      .usingJobData(buildJobDataMap(recipeId))
      .build()
    val trigger = newTrigger()
      .withIdentity(triggerKey(recipeId))
      .withSchedule(cronSchedule(bakeSchedule.quartzCronExpression))
      .build()
    scheduler.scheduleJob(jobDetail, trigger)
    Logger.info(s"Scheduled recipe [$recipeId] to bake with schedule [${bakeSchedule.quartzCronExpression}]")
  }

  def start(): Unit = scheduler.start()

  def shutdown(): Unit = scheduler.shutdown()

  private def jobKey(recipeId: RecipeId): JobKey = new JobKey(recipeId.value)
  private def triggerKey(recipeId: RecipeId): TriggerKey = new TriggerKey(recipeId.value)

  private def buildJobDataMap(recipeId: RecipeId): JobDataMap = {
    import BakeScheduler.JobDataKeys
    val map = new JobDataMap()
    map.put(JobDataKeys.RecipeId, recipeId)
    map.put(JobDataKeys.Runner, scheduledBakeRunner)
    map
  }

}

object BakeScheduler {

  object JobDataKeys {
    val RecipeId = "recipeId"
    val Runner = "runner"
  }

}
