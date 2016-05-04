package models

import com.gu.scanamo.DynamoFormat
import enumeratum._

sealed abstract class BakeStatus extends EnumEntry

object BakeStatus extends Enum[BakeStatus] {

  val values = findValues

  case object Running extends BakeStatus
  case object Complete extends BakeStatus
  case object Failed extends BakeStatus

  implicit val dynamoFormat = {
    DynamoFormat.coercedXmap[BakeStatus, String, IllegalArgumentException](withName)(_.entryName)
  }

}
