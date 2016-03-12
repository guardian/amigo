package models

case class BaseImage(
  id: BaseImageId,
  description: String,
  amiId: AmiId,
  builtinRoles: List[CustomisedRole])

