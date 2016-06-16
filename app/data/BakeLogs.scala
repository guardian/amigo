package data

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model._
import com.gu.cm.Identity
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.{ Scanamo, ScanamoFree, Table }
import models._

import scala.collection.JavaConverters._

class BakeLogs(table: Table[BakeLog]) {

  import DynamoFormats.dateTimeFormat
  import com.gu.scanamo.syntax._

  def save(bakeLog: BakeLog): ScanamoOps[Unit] = {
    // Make sure we don't try to save an empty string to Dynamo
    val safeMessageParts = bakeLog.messageParts.map(part => part.copy(text = if (part.text.nonEmpty) part.text else " "))
    val safeBakeLog = bakeLog.copy(messageParts = safeMessageParts)
    table.put(safeBakeLog).map(_ => ())
  }

  def list(bakeId: BakeId): ScanamoOps[Iterable[BakeLog]] = {
    table.query('bakeId -> bakeId).map(_.flatMap(_.toOption))
  }
}
