package data

import com.gu.scanamo._
import com.gu.scanamo.syntax._
import models._
import org.joda.time.DateTime

object BaseImages {
  import Dynamo._

  def create(id: BaseImageId,
    description: String,
    amiId: AmiId,
    builtinRoles: List[CustomisedRole],
    createdBy: String)(implicit dynamo: Dynamo): BaseImage = {
    val now = DateTime.now()
    val baseImage = BaseImage(id, description, amiId, builtinRoles, createdBy, createdAt = now, modifiedBy = createdBy, modifiedAt = now)

    table.put(baseImage).exec()
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
    table.put(updated).exec()
  }

  def list()(implicit dynamo: Dynamo): Iterable[BaseImage] = {
    table.scan().exec().flatMap(_.toOption)
  }

  def findById(id: BaseImageId)(implicit dynamo: Dynamo): Option[BaseImage] =
    table.get('id -> id).exec().flatMap(_.toOption)

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.baseImages.table

}
