package data

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.DynamoReadError
import org.joda.time.DateTime

object DynamoFormats {

  implicit val dateTimeFormat =
    DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](DateTime.parse)(_.toString)

  /*
  Special format to handle the two different ways that Scanamo can encode `None`:
   - Legacy (pre-0.3): write it as an explicit `NULL` property
   - Modern: skip the property completely (and presumably delete it if it is present)
   */
  implicit def legacyOptionFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Option[T]] = new DynamoFormat[Option[T]] {

    def read(av: AttributeValue): Xor[DynamoReadError, Option[T]] = {
      Option(av) match {
        case Some(property) =>
          if (Option(property.isNULL).exists(_.booleanValue))
            Xor.right(None) // legacy encoding of None
          else
            f.read(property).map(t => Some(t))
        case None =>
          Xor.right(None) // modern encoding of None
      }
    }

    def write(t: Option[T]): AttributeValue = t.map(f.write).orNull

    override val default = Some(None)

  }

}
