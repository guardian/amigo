package models

import org.scanamo.DynamoFormat
import org.scanamo.{ DynamoReadError, TypeCoercionError }
import enumeratum._
import cats.syntax.either._

sealed abstract class BakeStatus extends EnumEntry

object BakeStatus extends Enum[BakeStatus] {

  val values = findValues

  case object Running extends BakeStatus
  case object Complete extends BakeStatus
  case object Failed extends BakeStatus
  case object TimedOut extends BakeStatus
  case object DeletionScheduled extends BakeStatus

  implicit val dynamoFormat = {
    def fromString(s: String): Either[DynamoReadError, BakeStatus] = Either.fromOption(
      withNameOption(s),
      TypeCoercionError(new IllegalArgumentException(s"Invalid bake status: $s")))
    DynamoFormat.xmap[BakeStatus, String](fromString, _.entryName)
  }

}
