package models

import cats.data.Validated.Valid
import com.gu.scanamo.DynamoFormat
import play.api.mvc.PathBindable

case class RecipeId(value: String) extends StringId(value)

object RecipeId {

  implicit val pathBindable: PathBindable[RecipeId] = implicitly[PathBindable[String]].transform(RecipeId(_), _.value)

  implicit val dynamoFormat: DynamoFormat[RecipeId] =
    DynamoFormat.xmap(DynamoFormat.stringFormat)(value => Valid(RecipeId(value)))(_.value)

}

