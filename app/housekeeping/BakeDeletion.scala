package housekeeping

import data.{BakeLogs, Bakes, Dynamo}
import models.BakeId
import notification.NotificationSender
import org.quartz.SimpleScheduleBuilder
import prism.RecipeUsage
import services.{Loggable, PrismData}

/*
  This class deletes bakes that have been marked deleted
 */
class BakeDeletion(
    dynamo: Dynamo,
    amigoAwsAccount: String,
    prismAgents: PrismData,
    notificationSender: NotificationSender,
    frequencyMinutes: Int
) extends HousekeepingJob
    with Loggable {

  implicit private val implDynamo: Dynamo = dynamo
  implicit private val implPrismAgents: PrismData = prismAgents

  override val schedule =
    SimpleScheduleBuilder.repeatMinutelyForever(frequencyMinutes)

  def housekeep(): Unit = {
    log.info(s"Started bake deletion housekeeping")

    // get some bakes that have been deleted
    try {
      val deletedBakes = Bakes.findDeleted()

      if (deletedBakes.nonEmpty) {
        log.info(s"Found ${deletedBakes.size} bakes to delete")
      }

      // delete any AMIs
      val amis = deletedBakes.flatMap(_.amiId)
      val allAmis = RecipeUsage.allAmis(amis, amigoAwsAccount)
      notificationSender.sendHousekeepingTopicMessage(allAmis)

      // delete the logs and bakes
      deletedBakes.foreach { bake =>
        val bakeId = BakeId(bake.recipeId, bake.buildNumber)
        log.info(s"Deleting $bakeId")
        BakeLogs.delete(bakeId)
        Bakes.deleteById(bakeId)
        // avoid overwhelming the DB by pausing briefly before the next one
        Thread.sleep(2000)
      }
    } catch {
      case e: Exception =>
        log.error(s"Error raised during bake deletion housekeeping", e)
    }

  }
}
