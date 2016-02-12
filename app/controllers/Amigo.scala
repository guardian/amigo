package controllers

import _root_.packer.{ PackerListener, PackerRunner }
import features.FeaturesRepository
import models._
import play.api._
import play.api.mvc._

class Amigo extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
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
          |add-apt-repository "deb http://eu-west-1.ec2.archive.ubuntu.com/ubuntu/ vivid universe multiverse"
          |add-apt-repository "deb http://eu-west-1.ec2.archive.ubuntu.com/ubuntu/ vivid main restricted"
          |add-apt-repository "deb http://eu-west-1.ec2.archive.ubuntu.com/ubuntu/ vivid-updates universe multiverse"
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

  def createImage = Action {
    val bake = Bake(recipe, buildNumber = 123)
    val listener = new PackerListener {
      override def onProcessExited(exitCode: Int): Unit = Logger.info(s"Packer process completed with exit code $exitCode")

      override def onAmiCreated(amiId: AmiId): Unit = Logger.info(s"Packer created an AMI! AMI id = ${amiId.value}")

      override def onLineOfOutput(string: String): Unit = Logger.info(s"PACKER: $string")
    }
    PackerRunner.createImage(bake, listener)
    Ok("Kicked off Packer. Follow the logs for details.")
  }
}
