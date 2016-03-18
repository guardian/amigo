package models

import cats.data.Validated._
import cats.data.ValidatedNel
import com.gu.scanamo.{ TypeCoercionError, DynamoReadError, DynamoFormat }

case class BakeId(recipeId: RecipeId, buildNumber: Int)

object BakeId {

  // Bake ID is stored as a single string in Dynamo, e.g. "ubuntu-wily-java8 #123"
  private val DynamoFormatRegex = """(.+) #([0-9]+)""".r

  implicit val dynamoFormat: DynamoFormat[BakeId] = {
    def fromString(s: String): ValidatedNel[DynamoReadError, BakeId] = s match {
      case DynamoFormatRegex(recipeId, buildNumber) => valid(BakeId(RecipeId(recipeId), buildNumber.toInt))
      case _ => invalidNel(TypeCoercionError(new IllegalArgumentException(s"Invalid bake ID: $s")))
    }
    DynamoFormat.xmap(DynamoFormat.stringFormat)(fromString)(bakeId => s"${bakeId.recipeId.value} #${bakeId.buildNumber}")
  }

}

