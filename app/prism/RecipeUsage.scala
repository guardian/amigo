package prism

import models.{ Bake, Recipe, RecipeId }
import prism.Prism.{ Instance, LaunchConfiguration }
import services.PrismAgents

case class RecipeUsage(instances: Seq[Instance], launchConfigurations: Seq[LaunchConfiguration])

object RecipeUsage {

  def noUsage(): RecipeUsage = RecipeUsage(Seq.empty[Instance], Seq.empty[LaunchConfiguration])

  def apply(recipe: Recipe, bakes: Iterable[Bake])(implicit prismAgents: PrismAgents): RecipeUsage = {
    println(s"Finding usages for $recipe / $bakes")
    val bakedAmiIds = bakes.flatMap(_.amiId.map(_.value)).toList
    println(s"Got baked AMIs: $bakedAmiIds")
    val copiedAmiIds = prismAgents.copiedImages(bakedAmiIds.toSet).values.flatten.map(_.imageId)
    println(s"Got copied AMIs: $copiedAmiIds")
    val amiIds = bakedAmiIds ++ copiedAmiIds
    val instances = prismAgents.allInstances.filter(instance => amiIds.contains(instance.imageId))
    val launchConfigurations = prismAgents.allLaunchConfigurations.filter(lc => amiIds.contains(lc.imageId))
    RecipeUsage(instances, launchConfigurations)
  }

  def forAll(recipes: Iterable[Recipe], findBakes: RecipeId => Iterable[Bake])(implicit prismAgents: PrismAgents): Map[Recipe, RecipeUsage] = {
    recipes.map(r => r -> apply(r, findBakes(r.id))).toMap
  }

}
