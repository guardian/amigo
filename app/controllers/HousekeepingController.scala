package controllers

import com.gu.googleauth.AuthAction
import data._
import housekeeping.MarkOrphanedBakesForDeletion
import models.BakeId
import play.api.mvc._
import services.Loggable

class HousekeepingController(val authAction: AuthAction[AnyContent], components: ControllerComponents)(implicit dynamo: Dynamo)
    extends AbstractController(components) with Loggable {

  def showOrphans = authAction {
    val (errors, recipes) = Recipes.recipesWithErrors
    val recipeIds = recipes.map(recipe => recipe.id).toSet
    val orphanedBakes = MarkOrphanedBakesForDeletion.findOrphanedBakeIds(recipeIds, Bakes.scanForAll())
    Ok(views.html.housekeeping(orphanedBakes, errors.length))
  }

  def deleteOrphans(): Action[AnyContent] = authAction { implicit request =>
    for {
      formData <- request.body.asFormUrlEncoded.toSeq
      bakes <- formData.get("orphaned-bakes").toSeq
      bakeIdFromString <- bakes.map(BakeId.fromString)
    } yield {
      bakeIdFromString match {
        case Right(bakeId) => Bakes.markToDelete(bakeId)
        case Left(err) => log.warn(err.toString)
      }
    }

    Redirect(routes.HousekeepingController.showOrphans)
  }
}