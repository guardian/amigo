package data

import com.gu.scanamo.DynamoFormat
import org.joda.time.{ DateTime, DateTimeZone }

object DynamoFormats {

  implicit val dateTimeFormat = DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
    DateTime.parse(_).withZone(DateTimeZone.UTC))(_.toString)

}
