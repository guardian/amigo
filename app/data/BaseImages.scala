package data

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.model.{ ScanRequest, GetItemResult }
import com.gu.scanamo.{ DynamoReadError, Scanamo }
import models._

import scala.collection.JavaConverters._

object BaseImages {

  def list()(implicit dynamo: Dynamo): Iterable[BaseImage] = {
    val scanRequest = new ScanRequest(tableName)
    val items: Iterable[Option[ValidatedNel[DynamoReadError, BaseImage]]] =
      dynamo.client.scan(scanRequest).getItems.asScala.map { item => Scanamo.from[BaseImage](new GetItemResult().withItem(item)) }
    items.flatMap(_.flatMap(_.toOption))
  }

  def findById(id: BaseImageId)(implicit dynamo: Dynamo): Option[BaseImage] =
    Scanamo.get[BaseImageId, BaseImage](dynamo.client)(tableName)("id" -> id).flatMap(_.toOption)

  private def tableName(implicit dynamo: Dynamo) = dynamo.Tables.baseImages.name

}
