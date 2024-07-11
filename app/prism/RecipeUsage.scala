package prism

import data.{Bakes, Dynamo, PackageList, Recipes}
import models.{AmiId, Bake, BakeId, Recipe, RecipeId}
import play.api.libs.json.Json
import prism.Prism.{Image, Instance, LaunchConfiguration, LaunchTemplate}
import services.PrismData
import play.api.libs.json.OWrites

case class Ami(account: String, id: AmiId)

case class BakeUsage(
    amiId: AmiId,
    bake: Bake,
    viaCopy: Option[Image],
    instances: Seq[Instance],
    launchConfigurations: Seq[LaunchConfiguration],
    launchTemplates: Seq[LaunchTemplate]
)

case class SimpleBakeUsage(bakeId: BakeId, packageListS3Location: String)

object SimpleBakeUsage {
  implicit val writes: OWrites[SimpleBakeUsage] = Json.writes[SimpleBakeUsage]

  def fromBakeUsage(
      bakeUsage: BakeUsage,
      amigoDataBucket: Option[String]
  ): SimpleBakeUsage =
    SimpleBakeUsage(
      bakeUsage.bake.bakeId,
      PackageList.s3Url(
        bakeUsage.bake.bakeId,
        amigoDataBucket.getOrElse("unknown-bucket")
      )
    )

  def fromRecipeUsages(
      recipeUsages: Iterable[RecipeUsage],
      amigoDataBucket: Option[String]
  ): Iterable[SimpleBakeUsage] = {
    recipeUsages.flatMap { usage =>
      usage.bakeUsage.map(bu =>
        SimpleBakeUsage.fromBakeUsage(bu, amigoDataBucket)
      )
    }
  }
}

case class RecipeUsage(
    instances: Seq[Instance],
    launchConfigurations: Seq[LaunchConfiguration],
    launchTemplates: Seq[LaunchTemplate],
    bakeUsage: Seq[BakeUsage]
)

object RecipeUsage {

  def noUsage(): RecipeUsage = RecipeUsage(
    Seq.empty[Instance],
    Seq.empty[LaunchConfiguration],
    Seq.empty[LaunchTemplate],
    Seq.empty[BakeUsage]
  )

  def allAmis(amiIds: Iterable[AmiId], amigoAccount: String)(implicit
      prismAgents: PrismData
  ): List[Ami] = {
    val amis = amiIds.map(Ami(amigoAccount, _))

    val copiedAmiImages = prismAgents.copiedImages(amiIds.toSet).values.flatten
    val copiedAmis =
      copiedAmiImages.map(image => Ami(image.ownerId, image.imageId))

    amis.toList ++ copiedAmis
  }

  def apply(
      bakes: Iterable[Bake]
  )(implicit prismAgents: PrismData): RecipeUsage = {
    val bakedAmiLookupMap = bakes.flatMap(b => b.amiId.map(_ -> b)).toMap
    val bakedAmiIds = bakedAmiLookupMap.keys.toList
    val copiedAmis = prismAgents.copiedImages(bakedAmiIds.toSet).values.flatten
    val copiedAmiIds = copiedAmis.map(_.imageId)

    val amiIds = bakedAmiIds ++ copiedAmiIds
    val instances = prismAgents.allInstances.filter(instance =>
      amiIds.contains(instance.imageId)
    )
    val launchConfigurations = prismAgents.allLaunchConfigurations.filter(lc =>
      amiIds.contains(lc.imageId)
    )
    val launchTemplates =
      prismAgents.allLaunchTemplates.filter(lt => amiIds.contains(lt.imageId))
    val amis =
      (instances.map(_.imageId) ++ launchConfigurations.map(
        _.imageId
      ) ++ launchTemplates.map(_.imageId)).distinct

    val copiedAmiLookupMap = copiedAmis.map { image =>
      image.imageId -> image
    }.toMap

    val bakeUsage = amis.map { ami =>
      val maybeDirectBake = bakedAmiLookupMap.get(ami)
      val maybeCopy = copiedAmiLookupMap.get(ami)
      val bake = (maybeDirectBake, maybeCopy) match {
        case (Some(directBake), None) => directBake
        case (None, Some(copy))       => bakedAmiLookupMap(copy.copiedFromAMI)
        case _ =>
          throw new IllegalArgumentException(
            "AMI ID provided neither direct nor copied"
          )
      }
      val amiInstances = instances.filter(_.imageId == ami)
      val amiLc = launchConfigurations.filter(_.imageId == ami)
      val amiLt = launchTemplates.filter(_.imageId == ami)
      BakeUsage(ami, bake, maybeCopy, amiInstances, amiLc, amiLt)
    }

    RecipeUsage(instances, launchConfigurations, launchTemplates, bakeUsage)
  }

  def forAll(recipes: Iterable[Recipe], findBakes: RecipeId => Iterable[Bake])(
      implicit prismAgents: PrismData
  ): Map[Recipe, RecipeUsage] = {
    recipes.map(r => r -> apply(findBakes(r.id))).toMap
  }

  def getUsagesMap(recipes: Iterable[Recipe])(implicit
      prismAgents: PrismData,
      dynamo: Dynamo
  ): Map[Recipe, RecipeUsage] = {
    forAll(recipes, findBakes = recipeId => Bakes.list(recipeId))(prismAgents)
  }

  def getUsages(
      recipes: Iterable[Recipe]
  )(implicit prismAgents: PrismData, dynamo: Dynamo): Iterable[RecipeUsage] = {
    recipes.map(r => RecipeUsage(Bakes.list(r.id)))
  }

  def hasUsage(recipe: Recipe, usages: Map[Recipe, RecipeUsage]): Boolean = {
    usages
      .get(recipe)
      .exists(u => u.launchConfigurations.nonEmpty || u.instances.nonEmpty)
  }

  def amiUsages(recipeUsage: RecipeUsage, amiId: AmiId): RecipeUsage = {
    recipeUsage.copy(
      launchConfigurations =
        recipeUsage.launchConfigurations.filter(_.imageId == amiId),
      instances = recipeUsage.instances.filter(_.imageId == amiId),
      bakeUsage = recipeUsage.bakeUsage.filter(bu =>
        bu.amiId == amiId || bu.viaCopy.exists(i => i.imageId == amiId)
      )
    )
  }

  def amiIsUsed(recipeUsage: RecipeUsage, amiId: AmiId): Boolean = {
    amiUsages(recipeUsage, amiId).bakeUsage.nonEmpty
  }

  def bakeIsUsed(
      recipeUsage: RecipeUsage,
      amiId: Option[AmiId],
      recentCopies: Map[AmiId, Seq[Image]]
  ): Boolean = {
    val copies = amiId.flatMap(id => recentCopies.get(id)).getOrElse(Nil)
    amiId.exists(id => amiIsUsed(recipeUsage, id)) || copies.exists(c =>
      amiIsUsed(recipeUsage, c.imageId)
    )
  }

}
