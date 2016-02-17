package controllers

import packer.{ PackerListener, PackerRunner }
import akka.actor._
import features.FeaturesRepository
import models._
import play.api._
import play.api.libs.json.JsValue
import play.api.mvc._
import websockets._

class Amigo(webSocketMaster: ActorRef, applicationThunk: () => Application) extends Controller {

  def index = Action {
    Ok(views.html.sample())
  }

  val recipe = Recipe(
    id = RecipeId("ubuntu-wily-java8"),
    description = "Ubuntu Wily with only Java 8 installed",
    baseImage = BaseImage(
      id = BaseImageId("ubuntu-wily"),
      description = "Ubuntu 15.10 (Wily) hvm:ebs release 20160204 eu-west-1",
      amiId = AmiId("ami-cda312be"),
      initScript = ShellScript(
        """
          |add-apt-repository "deb http://eu-west-1.ec2.archive.ubuntu.com/ubuntu/ wily universe multiverse"
          |add-apt-repository "deb http://eu-west-1.ec2.archive.ubuntu.com/ubuntu/ wily main restricted"
          |add-apt-repository "deb http://eu-west-1.ec2.archive.ubuntu.com/ubuntu/ wily-updates universe multiverse"
          |sleep 1
          |
          |apt-get update
          |apt-get --yes upgrade
          |
          |## Ensure we don't swap unnecessarily
          |echo "vm.overcommit_memory=1" > /etc/sysctl.d/70-vm-overcommit.conf
          |
          |# Configure locale
          |locale-gen en_GB.UTF-8
        """.stripMargin
      ),
      mandatoryFeatures = Nil // TODO install a bunch of useful tools here (jq, aws-cli, git, etc.)
    ),
    features = FeaturesRepository.features.find(_.id == FeatureId("java8")).toSet
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

