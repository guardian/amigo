package controllers

import com.gu.googleauth.GoogleAuthConfig
import data.BaseImages.BaseImageUpdate
import packer.PackerRunner
import models._
import data._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.EventSource
import play.api.libs.iteratee.{ Enumerator, Concurrent }
import event._

import play.api.mvc._

class Amigo(eventsOut: Enumerator[BakeEvent], eventBus: EventBus, val authConfig: GoogleAuthConfig, val messagesApi: MessagesApi)(implicit dynamo: Dynamo) extends Controller with AuthActions with I18nSupport {
  import Amigo._

  def healthcheck = Action {
    Ok("OK")
  }

  def index = AuthAction {
    Ok(views.html.index())
  }

  def baseImages = AuthAction {
    Ok(views.html.baseImages(BaseImages.list()))
  }

  def showBaseImage(id: BaseImageId) = AuthAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound)(image => Ok(views.html.showBaseImage(image)))
  }

  def editBaseImage(id: BaseImageId) = AuthAction {
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      val form = Forms.editBaseImage.fill(BaseImageUpdate(
        description = image.description,
        amiId = image.amiId
      ))
      Ok(views.html.editBaseImage(image, form, Roles.list))
    }
  }

  def updateBaseImage(id: BaseImageId) = AuthAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      // TODO parse roles and variables
      Forms.editBaseImage.bindFromRequest.fold({ formWithErrors =>
        BadRequest(views.html.editBaseImage(image, formWithErrors, Roles.list))
      }, { update =>
        BaseImages.update(image, update, modifiedBy = request.user.fullName)
        Redirect(routes.Amigo.showBaseImage(id)).flashing("info" -> "Successfully updated base image")
      })
    }
  }

  def roles = AuthAction {
    Ok(views.html.roles(Roles.list))
  }

  def recipes = AuthAction {
    Ok(views.html.recipes(Recipes.list()))
  }

  def showRecipe(id: RecipeId) = AuthAction {
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val recentBakes = Bakes.list(id, limit = 20)
      Ok(views.html.showRecipe(recipe, recentBakes))
    }
  }

  def startBaking(recipeId: RecipeId) = AuthAction { request =>
    Recipes.findById(RecipeId("ubuntu-wily-java8")).fold[Result](NotFound) { recipe =>
      val buildNumber = Recipes.incrementAndGetBuildNumber(recipe.id).get
      val theBake = Bakes.create(recipe, buildNumber, startedBy = request.user.fullName)
      PackerRunner.createImage(theBake, eventBus)
      Redirect(routes.Amigo.showBake(recipeId, buildNumber))
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
    Ok.feed(eventsOut
      &> Concurrent.buffer(50) // TODO filter by bakeId
      &> EventSource[BakeEvent]()).as("text/event-stream")
  }
}

object Amigo {

  object Forms {
    val editBaseImage = Form(mapping(
      "description" -> text(maxLength = 10000),
      "amiId" -> nonEmptyText(maxLength = 16).transform[AmiId](AmiId.apply, _.value)
    )(BaseImageUpdate.apply)(BaseImageUpdate.unapply))
  }

}
