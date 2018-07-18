package housekeeping

import data.{ Bakes, Dynamo, Recipes }
import models.{ Bake, BakeId, RecipeId }
import org.quartz.SimpleScheduleBuilder
import play.api.Logger
import services.PrismAgents

object MarkOrphanedBakesForDeletion {
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

    Logger.info(s"Started marking orphaned bakes for deletion")
    val recipeIds = Recipes.list().map(_.id).toSet
    val bakes = Bakes.scanForAll()
    val orphanedBakeIds = MarkOrphanedBakesForDeletion.findOrphanedBakeIds(recipeIds, bakes)

    Logger.info(s"Marking ${orphanedBakeIds.size} orphaned bakes for deletion")
    orphanedBakeIds.foreach { bakeId =>
      Bakes.markToDelete(bakeId)
      Logger.info(s"Marked ${bakeId.toString} for deletion")
    }
  }
}