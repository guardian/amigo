package models

import cats.data.Xor
import com.gu.scanamo.DynamoFormat
import play.api.mvc.PathBindable

case class RecipeId(value: String) extends AnyVal with StringId

object RecipeId {

  implicit val pathBindable: PathBindable[RecipeId] = implicitly[PathBindable[String]].transform(RecipeId(_), _.value)

  implicit val dynamoFormat: DynamoFormat[RecipeId] =
    DynamoFormat.xmap[RecipeId, String](value => Xor.right(RecipeId(value)))(_.value)

}

