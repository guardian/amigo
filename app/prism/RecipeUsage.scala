package prism

import attempt._
import models.{ AmiId, Bake, Recipe, RecipeId }
import prism.Prism.{ Image, Instance, LaunchConfiguration }
import services.PrismAgents

import scala.concurrent.ExecutionContext

case class Ami(account: String, id: AmiId)

case class BakeUsage(amiId: AmiId, bake: Bake, viaCopy: Option[Image], instances: Seq[Instance], launchConfigurations: Seq[LaunchConfiguration])

case class RecipeUsage(instances: Seq[Instance], launchConfigurations: Seq[LaunchConfiguration], bakeUsage: Seq[BakeUsage])

object RecipeUsage {

  def noUsage(): RecipeUsage = RecipeUsage(Seq.empty[Instance], Seq.empty[LaunchConfiguration], Seq.empty[BakeUsage])

  def allAmis(amiIds: Iterable[AmiId], amigoAccount: String)(implicit prismAgents: PrismAgents, ec: ExecutionContext): Attempt[List[Ami]] = {
    val amis = amiIds.map(Ami(amigoAccount, _))

    for {
      copiedAmiImagesMap <- prismAgents.copiedImages(amiIds.toSet)
      copiedAmiImages = copiedAmiImagesMap.values.flatten
    } yield {
      val copiedAmis = copiedAmiImages.map(image => Ami(image.ownerId, image.imageId))
      amis.toList ++ copiedAmis
    }
  }

  def apply(bakes: Iterable[Bake])(implicit prismAgents: PrismAgents, ec: ExecutionContext): Attempt[RecipeUsage] = {
    val bakedAmiLookupMap = bakes.flatMap(b => b.amiId.map(_ -> b)).toMap
    val bakedAmiIds = bakedAmiLookupMap.keys.toList

    for {
      copiedAmiImagesMap <- prismAgents.copiedImages(bakedAmiIds.toSet)
      copiedAmis = copiedAmiImagesMap.values.flatten
      copiedAmiIds = copiedAmis.map(_.imageId)
      copiedAmiLookupMap = copiedAmis.map { image => image.imageId -> image }.toMap
      amisThatExist = bakedAmiIds ++ copiedAmiIds
      allInstances <- prismAgents.allInstances
      instances = allInstances.filter(instance => amisThatExist.contains(instance.imageId))
      allLaunchConfigurations <- prismAgents.allLaunchConfigurations
      launchConfigurations = allLaunchConfigurations.filter(lc => amisThatExist.contains(lc.imageId))
      amisInUse = (instances.map(_.imageId) ++ launchConfigurations.map(_.imageId)).distinct
    } yield {
      val bakeUsage = amisInUse.map { ami =>
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
  }

  def forAll(recipes: Iterable[Recipe], findBakes: RecipeId => Iterable[Bake])(implicit prismAgents: PrismAgents, ec: ExecutionContext): Attempt[Map[Recipe, RecipeUsage]] = {
    Attempt.traverse(recipes.toList)(r => apply(findBakes(r.id)).map(r -> _)).map(_.toMap)
  }

}
