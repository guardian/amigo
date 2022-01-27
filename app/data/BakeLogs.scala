package data

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.query.{ UniqueKeyConditions, UniqueKeys }
import com.gu.scanamo.syntax._
import models._
import services.Loggable

import scala.annotation.tailrec
import scala.collection.JavaConverters._

object BakeLogs extends Loggable {
  import Dynamo._

  private val BATCH_SIZE = 25
  private val BATCH_PAUSE = 1000

  def save(bakeLog: BakeLog)(implicit dynamo: Dynamo): Unit = {
    // Make sure we don't try to save an empty string to Dynamo
    val safeMessageParts = bakeLog.messageParts.map(part => part.copy(text = if (part.text.nonEmpty) part.text else " "))
    val safeBakeLog = bakeLog.copy(messageParts = safeMessageParts)
    table.put(safeBakeLog).exec()
  }

  def list(bakeId: BakeId)(implicit dynamo: Dynamo): Iterable[BakeLog] = {
    table.query('bakeId -> bakeId).exec().flatMap(_.toOption)
  }

  def delete(bakeId: BakeId, attempt: Int = 0)(implicit dynamo: Dynamo): Unit = {
    val logNumbers: Seq[Int] = table.query('bakeId -> bakeId).exec().flatMap(_.toOption).map(_.logNumber)
    val uniqueKeyTuples: Seq[(BakeId, Int)] = logNumbers.map((bakeId, _))
    uniqueKeyTuples.grouped(BATCH_SIZE).foreach { batch =>
      val uniqueKeys: UniqueKeys[_] = (HashAndRangeKeyNames('bakeId, 'logNumber), batch.toSet)
      doDelete(uniqueKeys)
      // avoid overwhelming the DB by pausing briefly after each batch
      Thread.sleep(BATCH_PAUSE)
    }
  }

  type UniqueKeySet = Set[Map[String, AttributeValue]]
  implicit private val identityKeyConditions: UniqueKeyConditions[UniqueKeySet] = new UniqueKeyConditions[UniqueKeySet] {
    override def asAVMap(t: UniqueKeySet): UniqueKeySet = t
  }

  @tailrec
  private def doDelete(logLines: UniqueKeys[_], attempt: Int = 0)(implicit dynamo: Dynamo): Unit = {
    val result = table.deleteAll(logLines).exec()
    val unprocessedKeys = for {
      batchResult <- result
      unprocessedItemsMap = batchResult.getUnprocessedItems.asScala
      unprocessedItems = unprocessedItemsMap.values.flatMap(_.asScala)
      unprocessedKeys = unprocessedItems.map(_.getDeleteRequest.getKey.asScala.toMap).toList
      unprocessedKey <- unprocessedKeys
    } yield unprocessedKey

    if (unprocessedKeys.nonEmpty) {
      log.warn(s"${unprocessedKeys.size} log entries not processed during deletion, trying again - attempt $attempt")
      // avoid overwhelming the DB by pausing briefly before mopping up
      Thread.sleep(BATCH_PAUSE)
      doDelete(UniqueKeys(unprocessedKeys.toSet), attempt + 1)
    }
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.bakeLogs.table

}
