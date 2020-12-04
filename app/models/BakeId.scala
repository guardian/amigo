package models

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.{ TypeCoercionError, DynamoReadError }

case class BakeId(recipeId: RecipeId, buildNumber: Int) {
  override def toString: String = s"${recipeId.value} #$buildNumber"
  def toIdString = s"${recipeId.value}-$buildNumber"
}

object BakeId {

  // Bake ID is stored as a single string in Dynamo, e.g. "ubuntu-wily-java8 #123"
  private val DynamoFormatRegex = """(.+) #([0-9]+)""".r

  def fromString(s: String): Either[DynamoReadError, BakeId] = s match {
    case DynamoFormatRegex(recipeId, buildNumber) => Right(BakeId(RecipeId(recipeId), buildNumber.toInt))
    case _ => Left(TypeCoercionError(new IllegalArgumentException(s"Invalid bake ID: $s")))
  }

  implicit val dynamoFormat: DynamoFormat[BakeId] = {
    DynamoFormat.xmap[BakeId, String](fromString)(bakeId => bakeId.toString)
  }

}

