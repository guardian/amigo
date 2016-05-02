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
import prism.Prism

class BakeController(
    eventsSource: Source[BakeEvent, _],
    prism: Prism,
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi,
    recipes: Recipes,
    bakes: Bakes,
    bakeLogs: BakeLogs)(implicit dynamo: Dynamo, packerConfig: PackerConfig, eventBus: EventBus) extends Controller with AuthActions with I18nSupport {

  import dynamo.exec

  def startBaking(recipeId: RecipeId) = AuthAction { request =>
    exec(recipes.findById(recipeId)).fold[Result](NotFound) { recipe =>
      val buildNumber = recipes.incrementAndGetBuildNumber(recipe.id).get
      val theBake = exec(bakes.create(recipe, buildNumber, startedBy = request.user.fullName))
      PackerRunner.createImage(theBake, prism, eventBus)
      Redirect(routes.BakeController.showBake(recipeId, buildNumber))
    }
  }

  def showBake(recipeId: RecipeId, buildNumber: Int) = AuthAction {
    exec(bakes.findById(recipeId, buildNumber)).fold[Result](NotFound) { bake =>
      val bakeLogList = exec(bakeLogs.list(BakeId(recipeId, buildNumber)))
      Ok(views.html.showBake(bake, bakeLogList))
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

