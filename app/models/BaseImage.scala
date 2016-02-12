package models

case class BaseImage(
  id: BaseImageId,
  description: String,
  amiId: AmiId,
  initScript: ShellScript,
  mandatoryFeatures: Seq[Feature])

