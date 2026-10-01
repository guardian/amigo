package components

import play.api.Configuration

object Config {
  def mandatoryConfig(configuration: Configuration, key: String): String =
    configuration
      .getOptional[String](key)
      .getOrElse(sys.error(s"Missing config key: $key"))
}
