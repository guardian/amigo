package models

import java.nio.file.Path

case class Feature(
  id: FeatureId,
  description: String,
  dependencies: Set[FeatureId],
  path: Path)

