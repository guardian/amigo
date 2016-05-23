package schedule

import data.{ Bakes, Recipes, Dynamo }
import event.EventBus
import models.RecipeId
import packer.{ PackerRunner, PackerConfig }
import play.api.Logger
import prism.Prism

class ScheduledBakeRunner(enabled: Boolean, prism: Prism, eventBus: EventBus, recipes: Recipes, bakes: Bakes)(implicit dynamo: Dynamo, packerConfig: PackerConfig) {

  def bake(recipeId: RecipeId): Unit = {
    if (!enabled) {
      Logger.info("Skipping scheduled bake because I am disabled")
    } else {
      dynamo.exec(recipes.findById(recipeId)) match {
        case Some(recipe) =>
          // sanity check: is the recipe actually scheduled?
          if (recipe.bakeSchedule.isEmpty) {
            Logger.warn(s"Skipping scheduled bake of recipe $recipeId because it does not have a bake schedule defined")
          } else {
            val buildNumber = recipes.incrementAndGetBuildNumber(recipe.id).get
            val theBake = dynamo.exec(bakes.create(recipe, buildNumber, startedBy = "scheduler"))

            Logger.info(s"Starting scheduled bake: ${theBake.bakeId}")
            PackerRunner.createImage(theBake, prism, eventBus)
          }
        case None =>
          Logger.warn(s"Skipping scheduled bake of recipe $recipeId because the recipe does not exist")
      }
    }
  }

}
