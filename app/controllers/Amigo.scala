package controllers

import packer.PackerRunner
import models._
import data._
import play.api.libs.EventSource
import play.api.libs.iteratee.{ Enumerator, Concurrent }
import event._

import play.api.mvc._

class Amigo(eventsOut: Enumerator[BakeEvent], eventBus: EventBus)(implicit dynamo: Dynamo) extends Controller {

  def index = Action {
    Ok(views.html.index())
  }

  def baseImages = Action {
    Ok(views.html.baseImages(BaseImages.list()))
  }

  def showBaseImage(id: BaseImageId) = Action {
    BaseImages.findById(id).fold[Result](NotFound)(image => Ok(views.html.showBaseImage(image)))
  }

  def roles = Action {
    Ok(views.html.roles(Roles.list))
  }

  def recipes = Action {
    Ok(views.html.recipes(Recipes.list()))
  }

  def showRecipe(id: RecipeId) = Action {
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val recentBakes = Bakes.list(id, limit = 20)
      Ok(views.html.showRecipe(recipe, recentBakes))
    }
  }

  def startBaking(recipeId: RecipeId) = Action {
    Recipes.findById(RecipeId("ubuntu-wily-java8")).fold[Result](NotFound) { recipe =>
      val buildNumber = Recipes.incrementAndGetBuildNumber(recipe.id).get
      val theBake = Bakes.create(recipe, buildNumber)
      PackerRunner.createImage(theBake, eventBus)
      Redirect(routes.Amigo.showBake(recipeId, buildNumber))
    }
  }

  def showBake(recipeId: RecipeId, buildNumber: Int) = Action {
    Bakes.findById(recipeId, buildNumber).fold[Result](NotFound) { bake =>
      val bakeLogs = BakeLogs.list(BakeId(recipeId, buildNumber))
      Ok(views.html.showBake(bake, bakeLogs))
    }
  }

  def bakeEvents(recipeId: RecipeId, buildNumber: Int) = Action { implicit req =>
    val bakeId = BakeId(recipeId, buildNumber)
    Ok.feed(eventsOut
      &> Concurrent.buffer(50) // TODO filter by bakeId
      &> EventSource[BakeEvent]()).as("text/event-stream")
  }
}

