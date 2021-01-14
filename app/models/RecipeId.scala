package models

import com.gu.scanamo.DynamoFormat
import play.api.libs.json.{ JsString, Json, OWrites, Writes }
import play.api.mvc.PathBindable

case class RecipeId(value: String) extends AnyVal with StringId

object RecipeId {
  implicit val pathBindable: PathBindable[RecipeId] = implicitly[PathBindable[String]].transform(RecipeId(_), _.value)

  implicit val dynamoFormat: DynamoFormat[RecipeId] =
    DynamoFormat.iso[RecipeId, String](RecipeId(_))(_.value)

}

