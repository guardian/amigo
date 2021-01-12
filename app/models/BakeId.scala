package models

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.{ DynamoReadError, TypeCoercionError }
import data.PackageList
import play.api.libs.json.{ JsObject, Json, OWrites, Writes }

case class BakeId(recipeId: RecipeId, buildNumber: Int) {
  override def toString: String = s"${recipeId.value} #$buildNumber"
}

object BakeId {

  implicit val writes: Writes[BakeId] = new Writes[BakeId] {
    def writes(bakeId: BakeId): JsObject = Json.obj(
      "recipeId" -> bakeId.recipeId.value,
      "buildNumber" -> bakeId.buildNumber
    )
  }

  def toFilename(bakeId: BakeId) = s"${bakeId.recipeId.value}--${bakeId.buildNumber}.txt"

  def toMetadata(bakeId: BakeId) = s"Recipe=${bakeId.recipeId.value},BuildNumber=${bakeId.buildNumber}"

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

