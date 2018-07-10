package data

import com.gu.scanamo.syntax._
import models._
import play.api.Logger

import scala.annotation.tailrec
import scala.collection.JavaConverters._

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

  @tailrec
  def delete(bakeId: BakeId, attempt: Int = 0)(implicit dynamo: Dynamo): Int = {
    val logNumbers: Seq[Int] = table.query('bakeId -> bakeId).exec().flatMap(_.toOption).map(_.logNumber)
    val uniqueKeyTuples: Set[(BakeId, Int)] = logNumbers.map((bakeId, _)).toSet
    val result = table.deleteAll((HashAndRangeKeyNames('bakeId, 'logNumber), uniqueKeyTuples)).exec()
    val unprocessedItems = for {
      batchResult <- result
      unprocessedItemsMap = batchResult.getUnprocessedItems.asScala
      unprocessedItems = unprocessedItemsMap.values.flatMap(_.asScala)
      unprocessedItem <- unprocessedItems
    } yield unprocessedItem
    if (unprocessedItems.nonEmpty) {
      Logger.warn(s"${unprocessedItems.size} log entries not processed during deletion, trying again - attempt $attempt")
      // avoid overwhelming the DB by pausing briefly before mopping up
      Thread.sleep(2000)
      delete(bakeId, attempt + 1)
    } else {
      0
    }
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.bakeLogs.table

}
