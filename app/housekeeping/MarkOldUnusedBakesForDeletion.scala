package housekeeping

import attempt.Attempt
import data.{ Bakes, Dynamo, Recipes }
import models.{ Bake, RecipeId }
import org.joda.time.{ DateTime, Duration }
import org.quartz.SimpleScheduleBuilder
import prism.RecipeUsage
import services.{ Loggable, PrismAgents }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object MarkOldUnusedBakesForDeletion {
  val MAX_AGE = 30
  val BATCH_SIZE = 100

  def getOldUnusedBakes(
    recipeIds: Set[RecipeId],
    now: DateTime,
    listBakes: RecipeId => Iterable[Bake],
    getRecipeUsage: Iterable[Bake] => Attempt[RecipeUsage])(implicit executionContext: ExecutionContext): Attempt[Set[Bake]] = {
    val allBakes = recipeIds.flatMap(listBakes)

    val oldBakes = allBakes.filter { bake =>
      val duration = new Duration(bake.startedAt, now)
      duration.getStandardDays > MAX_AGE
    }
    for {
      recipeUsage <- getRecipeUsage(oldBakes)
      usedBakes = recipeUsage.bakeUsage.map(_.bake).distinct.toSet
    } yield {
      oldBakes -- usedBakes
    }
  }
}

class MarkOldUnusedBakesForDeletion(prismAgents: PrismAgents, dynamo: Dynamo) extends HousekeepingJob with Loggable {
  override val schedule = SimpleScheduleBuilder.repeatHourlyForever(1)
  override val timeout = 10 minutes

  override def housekeep(executionContext: ExecutionContext): Attempt[Unit] = {
    implicit val implicitPrismAgents: PrismAgents = prismAgents
    implicit val implicitDynamo: Dynamo = dynamo
    implicit val implcitExecutionContext: ExecutionContext = executionContext
    log.info(s"Started marking old, unused bakes for deletion")
    val now = new DateTime()
    val recipeIds = Recipes.list().map(_.id).toSet
    for {
      oldUnusedBakes <- MarkOldUnusedBakesForDeletion.getOldUnusedBakes(recipeIds, now, Bakes.list, RecipeUsage.apply)
    } yield {
      if (oldUnusedBakes.nonEmpty) log.info(s"Found ${oldUnusedBakes.size} unused bakes over ${MarkOldUnusedBakesForDeletion.MAX_AGE} days old")

      val bakesToMark = oldUnusedBakes.take(MarkOldUnusedBakesForDeletion.BATCH_SIZE)
      if (bakesToMark.nonEmpty) log.info(s"Marking ${bakesToMark.size} unused bakes for deletion")

      bakesToMark.foreach { bake =>
        Bakes.markToDelete(bake.bakeId)
        log.info(s"Marked ${bake.bakeId} for deletion")
      }
    }
  }
}
