package services

import org.slf4j.LoggerFactory

trait Loggable {
  protected lazy val log = LoggerFactory.getLogger(getClass)
}
