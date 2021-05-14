package notification

import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{ Action, AwsAccount, Email, Notification, Preferred }
import data.{ Bakes, Dynamo }
import models.{ Bake, BakeId, BakeStatus, NotificationConfig }
import services.Loggable

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object BakeFailedNotifier extends Loggable {
  private val channel: Preferred = Preferred(Email)

  /**
   * Generates an anghammarad notification. The targets of the notification are generated based off the accounts to which
   * an encrypted copy has been requested. If no encrypted copies have been requested return None
   */
  def makeNotification(bake: Bake, bakeStatus: BakeStatus, config: NotificationConfig): Option[Notification] = {
    val targets = bake.recipe.encryptFor.map(m => AwsAccount(m.accountNumber))
    val statusString = bakeStatus.toString.toLowerCase()
    val actions = List(
      Action(s"View recipe ${bake.recipe} in AMIgo", s"$config.baseUrl/recipes/${bake.recipe.id}"),
      Action(s"Check bake log for ${bake.bakeId}", s"$config.baseUrl/recipes/${bake.recipe.id}/bakes/${bake.buildNumber}")
    )
    val stageString = if (config.amigoStage != "PROD") s"(stage ${config.amigoStage})" else ""
    if (targets.nonEmpty) {
      Some(
        Notification(
          s"AMIgo bake ${bake.bakeId} $statusString $stageString",
          s"""
             |Unfortunately a bake on ${bake.recipe.id} has $statusString. Sometimes failures happen due to AMIgo out of
             | memory errors and can be fixed simply by re-running the bake and changing the schedule to a less busy time.
             | See below for links to the AMIgo dashboard (VPN required) where you can debug this issue or kick off another bake.
             | For help, don't hesitate to contact the developer experience team: devx@theguardian.com.
             |""".stripMargin,
          actions,
          targets,
          channel,
          "AMIgo")
      )
    } else {
      log.info(s"No anghammarad targets available for bake ${bake.recipe.id},${bake.buildNumber} - likely no encrypted copies have been requested for the recipe.")
      None
    }
  }

  def logFailure(notificationSuccess: Boolean, configAvailable: Boolean, bakeId: BakeId): Unit = {
    if (!notificationSuccess) {
      if (configAvailable) {
        log.info(s"Failed to fetch bake ${bakeId} from DB. Bake failed notification not sent.")
      } else {
        log.info(s"Missing notification config - notification for bake failure ${bakeId} not sent")
      }
    }
  }

  def notifyBakeFailed(bakeId: BakeId, bakeStatus: BakeStatus, notificationConfig: Option[NotificationConfig])(implicit exec: ExecutionContext, dynamo: Dynamo): Unit = {
    val notificationResult = for {
      config <- notificationConfig
      bake <- Bakes.findById(bakeId.recipeId, bakeId.buildNumber)
      notification <- makeNotification(bake, bakeStatus, config)
    } yield {
      Anghammarad.notify(notification, config.snsTopicArn, config.snsClient).onComplete {
        case Success(value) =>
          log.info(s"Sent notification to anghammarard about recipe ${bake.recipe.id} bake failure. Status: $value")
        case Failure(exception) =>
          log.error(s"Failed to send notification about recipe ${bake.recipe.id} bake failure", exception)
      }
    }
    logFailure(notificationResult.isEmpty, notificationConfig.isEmpty, bakeId)
  }
}