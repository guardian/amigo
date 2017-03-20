package prism

import models.{ Bake, Recipe, RecipeId }
import prism.Prism.{ Instance, LaunchConfiguration }
import services.PrismAgents

case class RecipeUsage(recipeId: RecipeId, instances: Seq[Instance], launchConfigurations: Seq[LaunchConfiguration])

object RecipeUsage {

  def apply(recipe: Recipe, bakes: Iterable[Bake])(implicit prismAgents: PrismAgents): RecipeUsage = {
    val amiIds = bakes.flatMap(_.amiId.map(_.value)).toList
    val instances = prismAgents.allInstances.filter(instance => amiIds.contains(instance.imageId))
    val launchConfigurations = prismAgents.allLaunchConfigurations.filter(lc => amiIds.contains(lc.imageId))
    RecipeUsage(recipe.id, instances, launchConfigurations)
  }

  def forAll(recipes: Seq[Recipe], findBakes: RecipeId => Iterable[Bake])(implicit prismAgents: PrismAgents): Seq[RecipeUsage] = {
    recipes.map(r => apply(r, findBakes(r.id)))
  }

}
