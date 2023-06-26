package data

import org.scanamo.DynamoFormat
import org.joda.time.DateTime

object DynamoFormats {

  implicit val dateTimeFormat: DynamoFormat[DateTime] =
    DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
      DateTime.parse,
      _.toString
    )
}
