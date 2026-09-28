package packer

import models.packer.PackerVariablesConfig
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
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import services.AmiMetadata

import java.nio.file.Paths

class PackerBuildConfigGeneratorSpec extends AnyFlatSpec with Matchers {

  "generatePackerBuildConfig" should "tag volumes created for developer bakes" in {
    val now = DateTime.parse("2026-09-28T08:15:01.450Z")
    val baseImage = BaseImage(
      BaseImageId("ubuntu"),
      "Ubuntu",
      AmiId("ami-source"),
      Nil,
      "test",
      now,
      "test",
      now
    )
    val recipe = Recipe(
      id = RecipeId("kelvin-test"),
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
      buildNumber = 3,
      status = BakeStatus.Running,
      amiId = None,
      startedBy = "test",
      startedAt = now,
      deleted = false
    )
    implicit val packerConfig: PackerConfig =
      PackerConfig("DEV", None, None, None, None)

    val config = PackerBuildConfigGenerator.generatePackerBuildConfig(
      amigoStage = "DEV",
      bake,
      playbookFile = Paths.get("playbook.yml"),
      variables = PackerVariablesConfig(bake),
      awsAccountNumbers = Nil,
      sourceAmiMetadata = AmiMetadata("x86_64", "amd64"),
      amigoDataBucket = None,
      requiresXlargeBuilder = false
    )

    config.builders.head.run_volume_tags shouldBe Map(
      "AmigoStage" -> "DEV",
      "Stack" -> "amigo-packer"
    )
    config.builders.head.tags("Stack") shouldBe "amigo-packer"
  }
}
