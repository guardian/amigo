package housekeeping

import data.{ Bakes, Dynamo, Recipes }
import models.{ Bake, BakeId, RecipeId }
import org.quartz.SimpleScheduleBuilder
import play.api.Logger
import services.PrismAgents


/*
If a recipe has been deleted from a table but not the associated bake, then these
lonely bakes will linger on and never get picked up by the other housekeeping tasks
*/

object MarkOrphanedBakesForDeletion {
  val FAULT_TOLERANCE = 0

  def findOrphanedBakeIds(recipeIds: Set[RecipeId], bakes: List[Bake.DbModel]): List[BakeId] = {
    val orphanedBakes = bakes.filterNot(bake => recipeIds.contains(bake.recipeId))
    orphanedBakes.map(bake => BakeId(bake.recipeId, bake.buildNumber))
  }
}

class MarkOrphanedBakesForDeletion(prismAgents: PrismAgents, dynamo: Dynamo) extends HousekeepingJob {
  override val schedule = SimpleScheduleBuilder.repeatHourlyForever(24)

  override def housekeep(): Unit = {
    implicit val implicitPrismAgents: PrismAgents = prismAgents
    implicit val implicitDynamo: Dynamo = dynamo
    val (errors, recipes) = Recipes.recipesWithErrors
    errors match {
      case _ if errors.length > MarkOrphanedBakesForDeletion.FAULT_TOLERANCE =>
        Logger.info(s"Housekeeping found ${errors.length} database errors while searching for orphaned bakes")
        Logger.warn(s"${errors.length} errors exceeds the limit so orphaned bake deletion will not continue")
      case _ =>
        val bakes = Bakes.scanForAll()
        val orphanedBakeIds = MarkOrphanedBakesForDeletion.findOrphanedBakeIds(recipes.map(_.id).toSet, bakes)
        Logger.info(s"Marking ${orphanedBakeIds.size} orphaned bakes for deletion")
        orphanedBakeIds.foreach { bakeId =>
          Bakes.markToDelete(bakeId)
          Logger.info(s"Marked ${bakeId.toString} for deletion")
        }
    }
  }
}