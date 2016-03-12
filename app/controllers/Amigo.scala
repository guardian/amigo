package controllers

import packer.PackerRunner
import models._
import data.{ Dynamo, Recipes, Roles, BaseImages }
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

  val recipe = Recipe(
    id = RecipeId("ubuntu-wily-java8"),
    description = "Ubuntu Wily with only Java 8 installed",
    baseImage = BaseImage(
      id = BaseImageId("ubuntu-wily"),
      description = "Ubuntu 15.10 (Wily) hvm:ebs release 20160204 eu-west-1",
      amiId = AmiId("ami-cda312be"),
      builtinRoles = Seq(CustomisedRole(RoleId("ubuntu-wily-init"), Map.empty))
    ),
    roles = List(CustomisedRole(RoleId("java8"), Map.empty))
  )
  val theBake = Bake(recipe, buildNumber = 123)

  def bake = Action {
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

