package data

import org.scanamo.query.{UniqueKeyConditions, UniqueKeys}
import org.scanamo.syntax._
import models._
import org.scanamo.DynamoObject
import services.Loggable

object BakeLogs extends Loggable {

  import Dynamo._

  private val BATCH_SIZE = 25
  private val BATCH_PAUSE = 1000

  def save(bakeLog: BakeLog)(implicit dynamo: Dynamo): Unit = {
    // Make sure we don't try to save an empty string to Dynamo
    val safeMessageParts = bakeLog.messageParts.map(part =>
      part.copy(text = if (part.text.nonEmpty) part.text else " ")
    )
    val safeBakeLog = bakeLog.copy(messageParts = safeMessageParts)
    table.put(safeBakeLog).exec()
  }

  def list(bakeId: BakeId)(implicit dynamo: Dynamo): Iterable[BakeLog] = {
    table.query("bakeId" === bakeId).exec().flatMap(_.toOption)
  }

  def delete(bakeId: BakeId, attempt: Int = 0)(implicit
      dynamo: Dynamo
  ): Unit = {
    val logNumbers: Seq[Int] = table
      .query("bakeId" === bakeId)
      .exec()
      .flatMap(_.toOption)
      .map(_.logNumber)
    val uniqueKeyTuples: Seq[(BakeId, Int)] = logNumbers.map((bakeId, _))
    uniqueKeyTuples.grouped(BATCH_SIZE).foreach { batch =>
      val uniqueKeys: UniqueKeys[_] =
        (HashAndRangeKeyNames("bakeId", "logNumber"), batch.toSet)
      doDelete(uniqueKeys)
      // avoid overwhelming the DB by pausing briefly after each batch
      Thread.sleep(BATCH_PAUSE)
    }
  }

  type UniqueKeySet = Set[DynamoObject]
  implicit private val identityKeyConditions
      : UniqueKeyConditions[UniqueKeySet] =
    new UniqueKeyConditions[UniqueKeySet] {
      override def toDynamoObject(t: UniqueKeySet): Set[DynamoObject] = t
    }

  private def doDelete(logLines: UniqueKeys[_], attempt: Int = 0)(implicit
      dynamo: Dynamo
  ): Unit = {
    table.deleteAll(logLines).exec()
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.bakeLogs.table

}
