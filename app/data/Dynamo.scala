package data

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.{ DynamoFormat, Scanamo, Table => ScanamoTable }
import com.gu.scanamo.ops.ScanamoOps
import models.{ Bake, BakeLog, BaseImage, Recipe }
import services.Loggable

import scala.annotation.tailrec
import scala.util.Try

class Dynamo(val client: AmazonDynamoDB, stage: String) extends Loggable {
  import Dynamo._
  import DynamoFormats._

  object Tables {

    private def table[A: DynamoFormat](definition: CreateTableRequest): TableWrapper[A] = {
      val name = definition.getTableName
      val scanamoTable = ScanamoTable[A](name)
      TableWrapper(definition, scanamoTable)
    }

    val baseImages = table[BaseImage](
      new CreateTableRequest()
        .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
        .withAttributeDefinitions(new AttributeDefinition("id", ScalarAttributeType.S))
        .withTableName(tableName("base-images"))
        .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
    )

    val recipes = table[Recipe.DbModel](
      new CreateTableRequest()
        .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
        .withAttributeDefinitions(new AttributeDefinition("id", ScalarAttributeType.S))
        .withTableName(tableName("recipes"))
        .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
    )

    val bakes = table[Bake.DbModel](
      new CreateTableRequest()
        .withKeySchema(new KeySchemaElement("recipeId", KeyType.HASH), new KeySchemaElement("buildNumber", KeyType.RANGE))
        .withAttributeDefinitions(new AttributeDefinition("recipeId", ScalarAttributeType.S), new AttributeDefinition("buildNumber", ScalarAttributeType.N))
        .withTableName(tableName("bakes"))
        .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
    )

    val bakeLogs = table[BakeLog](
      new CreateTableRequest()
        .withKeySchema(new KeySchemaElement("bakeId", KeyType.HASH), new KeySchemaElement("logNumber", KeyType.RANGE))
        .withAttributeDefinitions(new AttributeDefinition("bakeId", ScalarAttributeType.S), new AttributeDefinition("logNumber", ScalarAttributeType.N))
        .withTableName(tableName("bake-logs"))
        .withProvisionedThroughput(new ProvisionedThroughput(10L, 10L))
    )

  }

  def initTables(): Unit = {
    import Tables._
    for (table <- Seq(baseImages, recipes, bakes, bakeLogs))
      createTableIfDoesNotExist(table)
  }

  private def tableName(suffix: String) = s"amigo-$stage-$suffix"

  private def createTableIfDoesNotExist(table: TableWrapper[_]): Unit = {
    if (Try(client.describeTable(table.name)).isFailure) {
      log.info(s"Creating Dynamo table ${table.name} ...")
      client.createTable(table.definition)
      waitForTableToBecomeActive(table.name)
    } else {
      log.info(s"Found Dynamo table ${table.name}")
    }
  }

  @tailrec
  private def waitForTableToBecomeActive(name: String): Unit = {
    Try(Option(client.describeTable(name).getTable)).toOption.flatten match {
      case Some(table) if table.getTableStatus == TableStatus.ACTIVE.toString => ()
      case _ =>
        log.info(s"Waiting for table $name to become active ...")
        Thread.sleep(500L)
        waitForTableToBecomeActive(name)
    }
  }

}

object Dynamo {

  case class TableWrapper[A](private[Dynamo] val definition: CreateTableRequest, table: ScanamoTable[A]) {
    val name = definition.getTableName
  }

  implicit class RichScanamoOps[A](val ops: ScanamoOps[A]) extends AnyVal {
    def exec()(implicit dynamo: Dynamo): A = Scanamo.exec(dynamo.client)(ops)
  }
}

