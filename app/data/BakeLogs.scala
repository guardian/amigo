package data

import com.gu.scanamo.syntax._
import models._

object BakeLogs {
  import cats.syntax.either._
  import Dynamo._

  def save(bakeLog: BakeLog)(implicit dynamo: Dynamo): Unit = {
    // Make sure we don't try to save an empty string to Dynamo
    val safeMessageParts = bakeLog.messageParts.map(part => part.copy(text = if (part.text.nonEmpty) part.text else " "))
    val safeBakeLog = bakeLog.copy(messageParts = safeMessageParts)
    table.put(safeBakeLog).exec()
  }

  def list(bakeId: BakeId)(implicit dynamo: Dynamo): Iterable[BakeLog] = {
    table.query('bakeId -> bakeId).exec().flatMap(_.toOption)
  }

  def delete(bakeId: BakeId)(implicit dynamo: Dynamo): Unit = {
    val logNumbers: Seq[Int] = table.query('bakeId -> bakeId).exec().flatMap(_.toOption).map(_.logNumber)
    val uniqueKeyTuples: Set[(BakeId, Int)] = logNumbers.map((bakeId, _)).toSet
    val result = table.deleteAll((HashAndRangeKeyNames('bakeId, 'logNumber), uniqueKeyTuples)).exec()
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.bakeLogs.table

}
