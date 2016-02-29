package controllers

import packer.PackerRunner
import models._
import _root_.data.{ Recipes, Roles, BaseImages }
import play.api.libs.EventSource
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import event._

import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Amigo(eventsOut: Enumerator[BakeEvent], eventBus: EventBus) extends Controller {

  def index = Action {
    Ok(views.html.index())
  }

  def baseImages = Action.async {
    BaseImages.list() map { images =>
      Ok(views.html.baseImages(images))
    }
  }

  def roles = Action {
    Ok(views.html.roles(Roles.list))
  }

  def recipes = Action.async {
    Recipes.list() map { recipes =>
      Ok(views.html.recipes(recipes))
    }
  }

  val recipe = Recipe(
    id = RecipeId("ubuntu-wily-java8"),
    description = "Ubuntu Wily with only Java 8 installed",
    baseImage = BaseImage(
      id = BaseImageId("ubuntu-wily"),
      description = "Ubuntu 15.10 (Wily) hvm:ebs release 20160204 eu-west-1",
      amiId = AmiId("ami-cda312be"),
      builtinRoles = Seq(RoleId("ubuntu-wily-init"))
    ),
    roles = Roles.list.filter(_ == RoleId("java8"))
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

