package prism

import data.{ Bakes, Dynamo, PackageList, Recipes }
import models.{ AmiId, Bake, BakeId, Recipe, RecipeId }
import play.api.libs.json.Json
import prism.Prism.{ Image, Instance, LaunchConfiguration }
import services.PrismAgents

case class Ami(account: String, id: AmiId)

case class BakeUsage(amiId: AmiId, bake: Bake, viaCopy: Option[Image], instances: Seq[Instance], launchConfigurations: Seq[LaunchConfiguration])

case class SimpleBakeUsage(bakeId: BakeId, packageListS3Location: String)

object SimpleBakeUsage {
  implicit val writes = Json.writes[SimpleBakeUsage]

  def fromBakeUsage(bakeUsage: BakeUsage, amigoDataBucket: Option[String]): SimpleBakeUsage =
    SimpleBakeUsage(
      bakeUsage.bake.bakeId,
      PackageList.s3Url(bakeUsage.bake.bakeId, amigoDataBucket.getOrElse("unknown-bucket"))
    )

  def fromRecipeUsages(recipeUsages: Iterable[RecipeUsage], amigoDataBucket: Option[String]): Iterable[SimpleBakeUsage] = {
    recipeUsages.flatMap { usage =>
      usage.bakeUsage.map(bu => SimpleBakeUsage.fromBakeUsage(bu, amigoDataBucket))
    }
  }
}

case class RecipeUsage(instances: Seq[Instance], launchConfigurations: Seq[LaunchConfiguration], bakeUsage: Seq[BakeUsage])

object RecipeUsage {

  def noUsage(): RecipeUsage = RecipeUsage(Seq.empty[Instance], Seq.empty[LaunchConfiguration], Seq.empty[BakeUsage])

  def allAmis(amiIds: Iterable[AmiId], amigoAccount: String)(implicit prismAgents: PrismAgents): List[Ami] = {
    val amis = amiIds.map(Ami(amigoAccount, _))

    val copiedAmiImages = prismAgents.copiedImages(amiIds.toSet).values.flatten
    val copiedAmis = copiedAmiImages.map(image => Ami(image.ownerId, image.imageId))

    amis.toList ++ copiedAmis
  }

  def apply(bakes: Iterable[Bake])(implicit prismAgents: PrismAgents): RecipeUsage = {
    val bakedAmiLookupMap = bakes.flatMap(b => b.amiId.map(_ -> b)).toMap
    val bakedAmiIds = bakedAmiLookupMap.keys.toList
    val copiedAmis = prismAgents.copiedImages(bakedAmiIds.toSet).values.flatten
    val copiedAmiIds = copiedAmis.map(_.imageId)

    val amiIds = bakedAmiIds ++ copiedAmiIds
    val instances = prismAgents.allInstances.filter(instance => amiIds.contains(instance.imageId))
    val launchConfigurations = prismAgents.allLaunchConfigurations.filter(lc => amiIds.contains(lc.imageId))
    val amis = (instances.map(_.imageId) ++ launchConfigurations.map(_.imageId)).distinct

    val copiedAmiLookupMap = copiedAmis.map { image => image.imageId -> image }.toMap

    val bakeUsage = amis.map { ami =>
      val maybeDirectBake = bakedAmiLookupMap.get(ami)
      val maybeCopy = copiedAmiLookupMap.get(ami)
      val bake = (maybeDirectBake, maybeCopy) match {
        case (Some(directBake), None) => directBake
        case (None, Some(copy)) => bakedAmiLookupMap(copy.copiedFromAMI)
        case _ => throw new IllegalArgumentException("AMI ID provided neither direct nor copied")
      }
      val amiInstances = instances.filter(_.imageId == ami)
      val amiLc = launchConfigurations.filter(_.imageId == ami)
      BakeUsage(ami, bake, maybeCopy, amiInstances, amiLc)
    }

    RecipeUsage(instances, launchConfigurations, bakeUsage)
  }

  def forAll(recipes: Iterable[Recipe], findBakes: RecipeId => Iterable[Bake])(implicit prismAgents: PrismAgents): Map[Recipe, RecipeUsage] = {
    recipes.map(r => r -> apply(findBakes(r.id))).toMap
  }

  def getUsagesMap(recipes: Iterable[Recipe])(implicit prismAgents: PrismAgents, dynamo: Dynamo): Map[Recipe, RecipeUsage] = {
    forAll(recipes, findBakes = recipeId => Bakes.list(recipeId))(prismAgents)
  }

  def getUsages(recipes: Iterable[Recipe])(implicit prismAgents: PrismAgents, dynamo: Dynamo): Iterable[RecipeUsage] = {
    recipes.map(r => RecipeUsage(Bakes.list(r.id)))
  }

  def hasUsage(recipe: Recipe, usages: Map[Recipe, RecipeUsage]): Boolean = {
    usages.get(recipe).exists(u => u.launchConfigurations.nonEmpty || u.instances.nonEmpty)
  }

  def amiIsUsed(recipeUsage: RecipeUsage, amiId: Option[AmiId]): Boolean = {
    amiId.exists { amiId =>
      val usages: Seq[BakeUsage] = recipeUsage.bakeUsage.filter(bu => bu.amiId == amiId || bu.viaCopy.exists(i => i.imageId == amiId))
      usages.nonEmpty
    }
  }

  def bakeIsUsed(recipeUsage: RecipeUsage, amiId: Option[AmiId], recentCopies: Map[AmiId, Seq[Image]]): Boolean = {
    val copies = amiId.flatMap(id => recentCopies.get(id)).getOrElse(Nil)
    amiIsUsed(recipeUsage, amiId) || copies.exists(c => amiIsUsed(recipeUsage, Some(c.imageId)))
  }

}
