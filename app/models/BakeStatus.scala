package models

import cats.data._
import enumeratum._
import cats.data.Validated._
import com.gu.scanamo.{ TypeCoercionError, DynamoReadError, DynamoFormat }

import scala.util.{ Failure, Success, Try }

sealed abstract class BakeStatus extends EnumEntry

object BakeStatus extends Enum[BakeStatus] {

  val values = findValues

  case object Running extends BakeStatus
  case object Complete extends BakeStatus
  case object Failed extends BakeStatus

  implicit val dynamoFormat = {
    def fromString(s: String): ValidatedNel[DynamoReadError, BakeStatus] = {
      Try(withName(s)) match {
        case Success(bakeStatus) => Valid(bakeStatus)
        case Failure(e) => invalidNel(TypeCoercionError(e.asInstanceOf[Exception]))
      }
    }
    DynamoFormat.xmap(DynamoFormat.stringFormat)(fromString)(_.entryName)
  }

}
