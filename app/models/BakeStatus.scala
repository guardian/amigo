package models

import cats.data.Xor
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.{ DynamoReadError, TypeCoercionError }
import enumeratum._

sealed abstract class BakeStatus extends EnumEntry

object BakeStatus extends Enum[BakeStatus] {

  val values = findValues

  case object Running extends BakeStatus
  case object Complete extends BakeStatus
  case object Failed extends BakeStatus

  implicit val dynamoFormat = {
    def fromString(s: String): Xor[DynamoReadError, BakeStatus] = withNameOption(s) match {
      case Some(bakeStatus) => Xor.right(bakeStatus)
      case None => Xor.left(TypeCoercionError(new IllegalArgumentException(s"Invalid bake status: $s")))
    }
    DynamoFormat.xmap[BakeStatus, String](fromString)(_.entryName)
  }

}
