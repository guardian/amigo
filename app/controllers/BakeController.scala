package controllers

import akka.stream.scaladsl.Source
import cats.data.OptionT
import com.gu.googleauth.GoogleAuthConfig
import data._
import event._
import packer._
import models._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.EventSource
import play.api.mvc._
import prism.Prism
import com.gu.scanamo.ops.ScanamoOps

class BakeController(
    eventsSource: Source[BakeEvent, _],
    prism: Prism,
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi,
    recipes: Recipes,
    bakes: Bakes,
    bakeLogs: BakeLogs)(implicit dynamo: Dynamo, packerConfig: PackerConfig, eventBus: EventBus) extends Controller with OpActions with I18nSupport {

  def startBaking(recipeId: RecipeId) = AuthOpAction { request =>
    (for {
      recipe <- OptionT(recipes.findById(recipeId))
      buildNumber = recipes.incrementAndGetBuildNumber(recipe.id).get
      bake <- OptionT.liftF(bakes.create(recipe, buildNumber, startedBy = request.user.fullName))
    } yield {
      PackerRunner.createImage(bake, prism, eventBus)
      Redirect(routes.BakeController.showBake(recipeId, buildNumber))
    }).getOrElse(
      NotFound
    )
  }

  def showBake(recipeId: RecipeId, buildNumber: Int) = AuthOpAction {
    (for {
      bake <- OptionT(bakes.findById(recipeId, buildNumber))
      bakeLogList <- OptionT.liftF(bakeLogs.list(BakeId(recipeId, buildNumber)))
    } yield {
      Ok(views.html.showBake(bake, bakeLogList))
    }).getOrElse(
      NotFound
    )
  }

  def bakeEvents(recipeId: RecipeId, buildNumber: Int) = AuthAction { implicit req =>
    val bakeId = BakeId(recipeId, buildNumber)
    val source = eventsSource
      .filter(_.bakeId == bakeId) // only include events relevant to this bake
      .via(EventSource.flow)
    Ok.chunked(source).as("text/event-stream")
  }

}

