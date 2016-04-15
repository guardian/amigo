package data

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.model.{ ScanRequest, GetItemResult }
import com.gu.scanamo.{ DynamoReadError, Scanamo }
import models._
import org.joda.time.DateTime

import scala.collection.JavaConverters._

object BaseImages {
  import DynamoFormats._

  def create(id: BaseImageId,
    description: String,
    amiId: AmiId,
    builtinRoles: List[CustomisedRole],
    createdBy: String)(implicit dynamo: Dynamo): BaseImage = {
    val now = DateTime.now()
    val baseImage = BaseImage(id, description, amiId, builtinRoles, createdBy, createdAt = now, modifiedBy = createdBy, modifiedAt = now)
    Scanamo.put(dynamo.client)(tableName)(baseImage)
    baseImage
  }

  def update(baseImage: BaseImage, description: String, amiId: AmiId, builtinRoles: List[CustomisedRole], modifiedBy: String)(implicit dynamo: Dynamo): Unit = {
    val updated = baseImage.copy(
      description = description,
      amiId = amiId,
      builtinRoles = builtinRoles,
      modifiedBy = modifiedBy,
      modifiedAt = DateTime.now()
    )
    Scanamo.put(dynamo.client)(tableName)(updated)
  }

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
