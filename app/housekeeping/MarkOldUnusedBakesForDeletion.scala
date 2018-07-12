package housekeeping

import data.{ Bakes, Dynamo, Recipes }
import models.{ Bake, RecipeId }
import org.joda.time.{ DateTime, Duration }
import org.quartz.SimpleScheduleBuilder
import play.api.Logger
import prism.RecipeUsage
import services.PrismAgents

object MarkOldUnusedBakesForDeletion {
  def getOldUnusedBakes(
    recipeIds: Set[RecipeId], now: DateTime,
    listBakes: RecipeId => Iterable[Bake],
    getRecipeUsage: Iterable[Bake] => RecipeUsage): Set[Bake] = {
    val allBakes = recipeIds.flatMap(listBakes)

    val oldBakes = allBakes.filter { bake =>
      val duration = new Duration(bake.startedAt, now)
      duration.getStandardDays > 30
    }
    val recipeUsage = getRecipeUsage(oldBakes)
    val usedBakes = recipeUsage.bakeUsage.map(_.bake).distinct.toSet

    oldBakes -- usedBakes
  }
}

class MarkOldUnusedBakesForDeletion(prismAgents: PrismAgents, dynamo: Dynamo) extends HousekeepingJob {
  override val schedule = SimpleScheduleBuilder.repeatHourlyForever(1)

  override def housekeep(): Unit = {
    implicit val implicitPrismAgents: PrismAgents = prismAgents
    implicit val implicitDynamo: Dynamo = dynamo
    Logger.info(s"Started marking old, unused bakes for deletion")
    val now = new DateTime()
    val recipeIds = Recipes.list().map(_.id).toSet
    val oldUnusedBakes = MarkOldUnusedBakesForDeletion.getOldUnusedBakes(recipeIds, now, Bakes.list, RecipeUsage.apply)
    Logger.info(s"Found ${oldUnusedBakes.size} unused bakes over 30 days old")

    val bakesToMark = oldUnusedBakes.take(100)
    Logger.info(s"Marking ${bakesToMark.size} unused bakes for deletion")

    bakesToMark.foreach { bake =>
      Bakes.markToDelete(bake.bakeId)
      Logger.info(s"Marked ${bake.bakeId} for deletion")
    }
  }
}
