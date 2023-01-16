package schedule

import data.{Bakes, Dynamo, Recipes}
import event.EventBus
import models.RecipeId
import packer.{PackerConfig, PackerRunner}
import services.{AmiMetadataLookup, Loggable, PrismData}

class ScheduledBakeRunner(
    stage: String,
    enabled: Boolean,
    prism: PrismData,
    eventBus: EventBus,
    ansibleVars: Map[String, String],
    amiMetadataLookup: AmiMetadataLookup,
    amigoDataBucket: Option[String],
    packerRunner: PackerRunner
)(implicit dynamo: Dynamo, packerConfig: PackerConfig)
    extends Loggable {

  def bake(recipeId: RecipeId): Unit = {
    if (!enabled) {
      log.info("Skipping scheduled bake because I am disabled")
    } else {
      Recipes.findById(recipeId) match {
        case Some(recipe) =>
          // sanity check: is the recipe actually scheduled?
          if (recipe.bakeSchedule.isEmpty) {
            log.warn(
              s"Skipping scheduled bake of recipe $recipeId because it does not have a bake schedule defined"
            )
          } else {
            Recipes.incrementAndGetBuildNumber(recipe.id) match {
              case Some(buildNumber) =>
                val theBake =
                  Bakes.create(recipe, buildNumber, startedBy = "scheduler")

                log.info(s"Starting scheduled bake: ${theBake.bakeId}")
                packerRunner.createImage(
                  stage,
                  theBake,
                  prism,
                  eventBus,
                  ansibleVars,
                  false,
                  amiMetadataLookup,
                  amigoDataBucket
                )
              case None =>
                log.warn(
                  s"Failed to get the next build number for recipe $recipeId"
                )
            }
          }
        case None =>
          log.warn(
            s"Skipping scheduled bake of recipe $recipeId because the recipe does not exist"
          )
      }
    }
  }

}
