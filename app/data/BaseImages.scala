package data

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model.{ GetItemResult, ScanRequest }
import com.gu.cm.Identity
import com.gu.scanamo.{ Scanamo, ScanamoFree, Table }
import com.gu.scanamo.syntax._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import models._
import org.joda.time.DateTime

import scala.collection.JavaConverters._

class BaseImages(table: Table[BaseImage]) {
  import DynamoFormats._

  def create(id: BaseImageId,
    description: String,
    amiId: AmiId,
    builtinRoles: List[CustomisedRole],
    createdBy: String): ScanamoOps[BaseImage] = {
    val now = DateTime.now()
    val baseImage = BaseImage(id, description, amiId, builtinRoles, createdBy, createdAt = now, modifiedBy = createdBy, modifiedAt = now)
    table.put(baseImage).map(_ => baseImage)
  }

  def update(baseImage: BaseImage, description: String, amiId: AmiId, builtinRoles: List[CustomisedRole], modifiedBy: String): ScanamoOps[Unit] = {
    val updated = baseImage.copy(
      description = description,
      amiId = amiId,
      builtinRoles = builtinRoles,
      modifiedBy = modifiedBy,
      modifiedAt = DateTime.now()
    )
    table.put(updated).map(_ => ())
  }

  def list(): ScanamoOps[Iterable[BaseImage]] = {
    table.scan().map(_.flatMap(_.toOption))
  }

  def findById(id: BaseImageId): ScanamoOps[Option[BaseImage]] =
    table.get('id -> id).map(_.flatMap(_.toOption))
}
