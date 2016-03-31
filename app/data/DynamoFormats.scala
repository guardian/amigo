package data

import cats.data.Validated
import com.gu.scanamo.DynamoFormat
import org.joda.time.DateTime

object DynamoFormats {

  implicit val dateTimeFormat = DynamoFormat.xmap(DynamoFormat.stringFormat)(d => Validated.valid(new DateTime(d)))(_.toString)

}
