package controllers

import akka.stream.scaladsl.Source
import com.gu.googleauth.GoogleAuthConfig
import data._
import event._
import packer._
import models._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.EventSource
import play.api.mvc._
import services.{ Loggable, PrismAgents }

class BakeController(
  eventsSource: Source[BakeEvent, _],
  prism: PrismAgents,
  val authConfig: GoogleAuthConfig,
  val messagesApi: MessagesApi,
  ansibleVars: Map[String, String],
  debugAvailable: Boolean)(implicit dynamo: Dynamo, packerConfig: PackerConfig, eventBus: EventBus)
    extends Controller with AuthActions with I18nSupport with Loggable {

  def startBaking(recipeId: RecipeId, debug: Boolean) = AuthAction { request =>
    Recipes.findById(recipeId).fold[Result](NotFound) { recipe =>
      Recipes.incrementAndGetBuildNumber(recipe.id) match {
        case Some(buildNumber) =>
          val theBake = Bakes.create(recipe, buildNumber, startedBy = request.user.fullName)
          PackerRunner.createImage(theBake, prism, eventBus, ansibleVars, debugAvailable && debug)
          Redirect(routes.BakeController.showBake(recipeId, buildNumber))
        case None =>
          val message = s"Failed to get the next build number for recipe $recipeId"
          log.warn(message)
          InternalServerError(message)
      }
    }
  }

  def showBake(recipeId: RecipeId, buildNumber: Int) = AuthAction {
    Bakes.findById(recipeId, buildNumber).fold[Result](NotFound) { bake =>
      val bakeLogs = BakeLogs.list(BakeId(recipeId, buildNumber))
      Ok(views.html.showBake(bake, bakeLogs))
    }
  }

  def bakeEvents(recipeId: RecipeId, buildNumber: Int) = AuthAction { implicit req =>
    val bakeId = BakeId(recipeId, buildNumber)
    val source = eventsSource
      .filter(_.bakeId == bakeId) // only include events relevant to this bake
      .via(EventSource.flow)
    Ok.chunked(source).as("text/event-stream")
  }

}