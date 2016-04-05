package models

import org.joda.time.DateTime

case class BaseImage(
  id: BaseImageId,
  description: String,
  amiId: AmiId,
  builtinRoles: List[CustomisedRole],
  createdBy: String,
  createdAt: DateTime,
  modifiedBy: String,
  modifiedAt: DateTime)

