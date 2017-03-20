package prism

import models.{ Bake, Recipe, RecipeId }
import prism.Prism.{ Instance, LaunchConfiguration }
import services.PrismAgents

case class RecipeUsage(instances: Seq[Instance], launchConfigurations: Seq[LaunchConfiguration])

object RecipeUsage {

  def noUsage(): RecipeUsage = RecipeUsage(Seq.empty[Instance], Seq.empty[LaunchConfiguration])

  def apply(recipe: Recipe, bakes: Iterable[Bake])(implicit prismAgents: PrismAgents): RecipeUsage = {
    val amiIds = bakes.flatMap(_.amiId.map(_.value)).toList
    val instances = prismAgents.allInstances.filter(instance => amiIds.contains(instance.imageId))
    val launchConfigurations = prismAgents.allLaunchConfigurations.filter(lc => amiIds.contains(lc.imageId))
    RecipeUsage(instances, launchConfigurations)
  }

  def forAll(recipes: Iterable[Recipe], findBakes: RecipeId => Iterable[Bake])(implicit prismAgents: PrismAgents): Map[Recipe, RecipeUsage] = {
    recipes.map(r => r -> apply(r, findBakes(r.id))).toMap
  }

}
