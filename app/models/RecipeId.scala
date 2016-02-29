package models

import play.api.mvc.PathBindable

case class RecipeId(value: String)

object RecipeId {

  implicit val pathBindable: PathBindable[RecipeId] = implicitly[PathBindable[String]].transform(RecipeId(_), _.value)

}

