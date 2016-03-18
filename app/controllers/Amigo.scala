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

  def roles = Action {
    Ok(views.html.roles(Roles.list))
  }

  def recipes = Action {
    Ok(views.html.recipes(Recipes.list()))
  }

  def bake = Action {
    val recipe = Recipes.findById(RecipeId("ubuntu-wily-java8")).get
    val buildNumber = Recipes.incrementAndGetBuildNumber(recipe.id).get
    val theBake = Bakes.create(recipe, buildNumber)
    PackerRunner.createImage(theBake, eventBus)
    Ok(views.html.bake(theBake))
  }

  def sse(recipeId: RecipeId, buildNumber: Int) = Action { implicit req =>
    val bakeId = BakeId(recipeId, buildNumber)
    Ok.feed(eventsOut
      &> Concurrent.buffer(50) // TODO filter by bakeId
      &> EventSource[BakeEvent]()).as("text/event-stream")
  }
}

