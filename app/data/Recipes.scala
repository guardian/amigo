package data

import models._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Recipes {

  private val recipe: Future[Option[Recipe]] = {
    BaseImages.findById(BaseImageId("ubuntu-wily")) map { maybeBaseImage =>
      maybeBaseImage map { baseImage =>
        Recipe(
          id = RecipeId("ubuntu-wily-java8"),
          description = "Ubuntu Wily with only Java 8 installed",
          baseImage = baseImage,
          roles = Roles.list.filter(_ == RoleId("java8"))
        )
      }
    }
  }

  def list(): Future[Iterable[Recipe]] = recipe.map(_.toIterable)

  def findById(id: RecipeId): Future[Option[Recipe]] = list().map(_.find(_.id == id))

}
