package controllers

import com.gu.googleauth.GoogleAuthConfig
import data._
import housekeeping.MarkOrphanedBakesForDeletion
import models.BakeId
import play.api.Logger
import play.api.mvc._

class HousekeepingController(val authConfig: GoogleAuthConfig)(implicit dynamo: Dynamo) extends Controller with AuthActions {
  private val (errors, recipes) = Recipes.recipesWithErrors
  private val recipeIds = recipes.map(recipe => recipe.id).toSet
  private val orphanedBakes = MarkOrphanedBakesForDeletion.findOrphanedBakeIds(recipeIds, Bakes.scanForAll())

  def showOrphans = AuthAction {
    Ok(views.html.housekeeping(orphanedBakes, errors.length))
  }

  def deleteOrphans(): Action[AnyContent] = AuthAction { implicit request =>
    for {
      formData <- request.body.asFormUrlEncoded.toSeq
      bakes <- formData.get("orphaned-bakes").toSeq
      bakeIdFromString <- bakes.map(BakeId.fromString)
    } yield {
      bakeIdFromString match {
        case Right(bakeId) => Bakes.markToDelete(bakeId)
        case Left(err) => Logger.warn(err.toString)
      }
    }

    Redirect(routes.HousekeepingController.showOrphans())
  }
}