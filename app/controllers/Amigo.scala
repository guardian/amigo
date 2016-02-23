package controllers

import packer.{ PackerListener, PackerRunner }
import models._
import _root_.data.{ Recipes, Roles, BaseImages }
import websockets._

import play.api._
import play.api.libs.json.JsValue
import play.api.mvc._

import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global

class Amigo(webSocketMaster: ActorRef, applicationThunk: () => Application) extends Controller {

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
    val listener = new PackerListener {
      override def onProcessExited(exitCode: Int): Unit = {
        Logger.info(s"Packer process completed with exit code $exitCode")
        webSocketMaster ! PackerProcessExited(theBake.bakeId, exitCode)
      }

      override def onAmiCreated(amiId: AmiId): Unit = {
        Logger.info(s"Packer created an AMI! AMI id = ${amiId.value}")
        webSocketMaster ! AmiCreated(theBake.bakeId, amiId)
      }

      override def onLineOfOutput(line: String): Unit = {
        Logger.info(s"PACKER: $line")
        webSocketMaster ! PackerOutput(theBake.bakeId, line)
      }
    }
    PackerRunner.createImage(theBake, listener)
    Ok(views.html.bake(theBake))
  }

  def socket = {
    val bakeId = theBake.bakeId // TODO construct bake ID from URL params
    implicit val application = applicationThunk() // to avoid a ridiculous cyclic dependency
    WebSocket.acceptWithActor[String, JsValue] { request =>
      out =>
        Props(new WebSocketActor(bakeId, webSocketMaster, out))
    }
  }

}

