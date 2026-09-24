package packer

import event.BakeEvent.PackerProcessExited
import event.EventBus
import models.{
  AmiId,
  Bake,
  BakeStatus,
  BaseImage,
  BaseImageId,
  Recipe,
  RecipeId
}
import org.joda.time.DateTime
import org.mockito.Mockito.{verify, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import services.{AmiMetadataLookup, PrismData}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class PackerRunnerSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  "createImage" should "report a failed Packer process when AMI metadata is unavailable" in {
    val sourceAmi = "ami-missing"
    val now = DateTime.now
    val baseImage = BaseImage(
      id = BaseImageId("missing-ami"),
      description = "Missing AMI",
      amiId = AmiId(sourceAmi),
      builtinRoles = Nil,
      createdBy = "test",
      createdAt = now,
      modifiedBy = "test",
      modifiedAt = now
    )
    val recipe = Recipe(
      id = RecipeId("missing-ami-recipe"),
      description = None,
      baseImage,
      diskSize = None,
      roles = Nil,
      createdBy = "test",
      createdAt = now,
      modifiedBy = "test",
      modifiedAt = now,
      bakeSchedule = None,
      encryptFor = Nil
    )
    val bake = Bake(
      recipe,
      buildNumber = 1,
      status = BakeStatus.Running,
      amiId = None,
      startedBy = "test",
      startedAt = now,
      deleted = false
    )
    val amiMetadataLookup = mock[AmiMetadataLookup]
    val eventBus = mock[EventBus]

    when(amiMetadataLookup.lookupMetadataFor(sourceAmi))
      .thenReturn(Left(s"No ami with ID $sourceAmi found"))

    implicit val packerConfig: PackerConfig =
      PackerConfig("DEV", None, None, None, None)

    val exitCode = Await.result(
      new PackerRunner(maxInstances = 1).createImage(
        stage = "DEV",
        bake,
        mock[PrismData],
        eventBus,
        ansibleVars = Map.empty,
        debug = false,
        amiMetadataLookup,
        amigoDataBucket = None
      ),
      1.second
    )

    exitCode shouldBe 1
    verify(eventBus).publish(PackerProcessExited(bake.bakeId, exitCode = 1))
  }
}
