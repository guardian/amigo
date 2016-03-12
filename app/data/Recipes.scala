package data

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.model.{ GetItemResult, ScanRequest }
import com.gu.scanamo.{ DynamoReadError, Scanamo }
import models.Recipe.DbModel
import models.RecipeId
import models._

import scala.collection.JavaConverters._

object Recipes {

  def list()(implicit dynamo: Dynamo): Iterable[Recipe] = {
    val scanRequest = new ScanRequest(tableName)
    val dbModels: Iterable[Option[ValidatedNel[DynamoReadError, DbModel]]] =
      dynamo.client.scan(scanRequest).getItems.asScala.map { item => Scanamo.from[Recipe.DbModel](new GetItemResult().withItem(item)) }
    for {
      dbModel <- dbModels.flatMap(_.flatMap(_.toOption))
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe(
        id = dbModel.id,
        description = dbModel.description,
        baseImage = baseImage,
        roles = dbModel.roles
      )
    }
  }

  def findById(id: RecipeId)(implicit dynamo: Dynamo): Option[Recipe] = {
    for {
      dbModel <- Scanamo.get[RecipeId, DbModel](dynamo.client)(tableName)("id" -> id).flatMap(_.toOption)
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe(
        id = dbModel.id,
        description = dbModel.description,
        baseImage = baseImage,
        roles = dbModel.roles
      )
    }
  }

  private def tableName(implicit dynamo: Dynamo) = dynamo.Tables.recipes.name

}
