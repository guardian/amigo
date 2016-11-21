package data

import models._
import com.gu.scanamo.syntax._

object BakeLogs {
  import Dynamo._
  import cats.syntax.either._

  def save(bakeLog: BakeLog)(implicit dynamo: Dynamo): Unit = {
    // Make sure we don't try to save an empty string to Dynamo
    val safeMessageParts = bakeLog.messageParts.map(part => part.copy(text = if (part.text.nonEmpty) part.text else " "))
    val safeBakeLog = bakeLog.copy(messageParts = safeMessageParts)
    table.put(safeBakeLog).exec()
  }

  def list(bakeId: BakeId)(implicit dynamo: Dynamo): Iterable[BakeLog] = {
    table.query('bakeId -> bakeId).exec().flatMap(_.toOption)
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.bakeLogs.table

}
