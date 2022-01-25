package models

import data.{ Bakes, Dynamo }
import notification.BakeFailedNotifier
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext

case class Bake(recipe: Recipe,
    buildNumber: Int,
    status: BakeStatus,
    amiId: Option[AmiId],
    startedBy: String,
    startedAt: DateTime,
    deleted: Boolean) {
  val bakeId = BakeId(recipe.id, buildNumber)
}

object Bake {

  case class DbModel(
      recipeId: RecipeId,
      buildNumber: Int,
      status: BakeStatus,
      amiId: Option[AmiId],
      startedBy: String,
      startedAt: DateTime,
      deleted: Option[Boolean]) {
    val bakeId = BakeId(recipeId, buildNumber)
  }

  def domain2db(bake: Bake): DbModel = DbModel(
    recipeId = bake.recipe.id,
    buildNumber = bake.buildNumber,
    status = bake.status,
    amiId = bake.amiId,
    startedBy = bake.startedBy,
    startedAt = bake.startedAt,
    deleted = Some(bake.deleted)
  )

  def db2domain(dbModel: DbModel, recipe: Recipe): Bake = Bake(
    recipe = recipe,
    buildNumber = dbModel.buildNumber,
    status = dbModel.status,
    amiId = dbModel.amiId,
    startedBy = dbModel.startedBy,
    startedAt = dbModel.startedAt,
    deleted = dbModel.deleted.getOrElse(false)
  )

  def updateStatusAndNotifyFailure(bakeId: BakeId, status: BakeStatus, notificationConfig: Option[NotificationConfig])(implicit dynamo: Dynamo, exec: ExecutionContext): Unit = {
    if (status == BakeStatus.Failed || status == BakeStatus.TimedOut) {
      BakeFailedNotifier.notifyBakeFailed(bakeId, status, notificationConfig)
    }
    Bakes.updateStatus(bakeId, status)
  }
}