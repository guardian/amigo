package data

import org.scanamo.{ DynamoFormat, Scanamo, Table => ScanamoTable }
import org.scanamo.ops.ScanamoOps
import org.scanamo.generic.auto._
import models.{ Bake, BakeLog, BaseImage, Recipe }
import services.Loggable
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.annotation.tailrec
import scala.util.Try

class Dynamo(val client: DynamoDbClient, stage: String) extends Loggable {
  import Dynamo._
  import DynamoFormats._

  object Tables {

    private def table[A: DynamoFormat](definition: CreateTableRequest): TableWrapper[A] = {
      val name = definition.tableName
      val scanamoTable = ScanamoTable[A](name)
      TableWrapper(definition, scanamoTable)
    }

    private def generateKeySchemaElement(atttributeName: String, keyType: KeyType): KeySchemaElement = {
      KeySchemaElement.builder()
        .attributeName(atttributeName)
        .keyType(keyType)
        .build()
    }

    def generateAttributeDefinition(attributeName: String, attributeType: ScalarAttributeType): AttributeDefinition = {
      AttributeDefinition.builder()
        .attributeName(attributeName)
        .attributeType(attributeType)
        .build()
    }

    def generateProvisionedThroughtput(readCapacity: Long, writeCapacity: Long): ProvisionedThroughput = {
      ProvisionedThroughput.builder()
        .readCapacityUnits(readCapacity)
        .writeCapacityUnits(writeCapacity)
        .build()
    }

    val baseImages = table[BaseImage](
      CreateTableRequest.builder()
        .keySchema(generateKeySchemaElement("id", KeyType.HASH))
        .attributeDefinitions(generateAttributeDefinition("id", ScalarAttributeType.S))
        .tableName(tableName("base-images"))
        .provisionedThroughput(generateProvisionedThroughtput(1L, 1L))
        .build())

    val recipes = table[Recipe.DbModel](
      CreateTableRequest.builder()
        .keySchema(generateKeySchemaElement("id", KeyType.HASH))
        .attributeDefinitions(generateAttributeDefinition("id", ScalarAttributeType.S))
        .tableName(tableName("recipes"))
        .provisionedThroughput(generateProvisionedThroughtput(1L, 1L))
        .build())

    val bakes = table[Bake.DbModel](
      CreateTableRequest.builder()
        .keySchema(generateKeySchemaElement("recipeId", KeyType.HASH), generateKeySchemaElement("buildNumber", KeyType.RANGE))
        .attributeDefinitions(generateAttributeDefinition("recipeId", ScalarAttributeType.S), generateAttributeDefinition("buildNumber", ScalarAttributeType.N))
        .tableName(tableName("bakes"))
        .provisionedThroughput(generateProvisionedThroughtput(1L, 1L))
        .globalSecondaryIndexes(
          GlobalSecondaryIndex.builder()
            .indexName("DeletedIndex")
            .keySchema(generateKeySchemaElement("deleted", KeyType.HASH))
            .projection(Projection.builder().projectionType("ALL").build())
            .provisionedThroughput(generateProvisionedThroughtput(1L, 1L))
            .build()
        ).build())

    val bakeLogs = table[BakeLog](
      CreateTableRequest.builder()
        .keySchema(generateKeySchemaElement("bakeId", KeyType.HASH), generateKeySchemaElement("logNumber", KeyType.RANGE))
        .attributeDefinitions(generateAttributeDefinition("bakeId", ScalarAttributeType.S), generateAttributeDefinition("logNumber", ScalarAttributeType.N))
        .tableName(tableName("bake-logs"))
        .provisionedThroughput(generateProvisionedThroughtput(10L, 10L))
        .build())

  }

  def generateDescribeTableRequest(tableName: String): DescribeTableRequest = {
    DescribeTableRequest.builder()
      .tableName(tableName)
      .build()
  }

  def initTables(): Unit = {
    import Tables._
    for (table <- Seq(baseImages, recipes, bakes, bakeLogs))
      createTableIfDoesNotExist(table)
  }

  private def tableName(suffix: String) = s"amigo-$stage-$suffix"

  private def createTableIfDoesNotExist(table: TableWrapper[_]): Unit = {
    if (Try(client.describeTable(generateDescribeTableRequest(table.name))).isFailure) {
      log.info(s"Creating Dynamo table ${table.name} ...")
      client.createTable(table.definition)
      waitForTableToBecomeActive(table.name)
    } else {
      log.info(s"Found Dynamo table ${table.name}")
    }
  }

  @tailrec
  private def waitForTableToBecomeActive(name: String): Unit = {
    Try(Option(client.describeTable(generateDescribeTableRequest(name)))).toOption.flatten match {
      case Some(table) if table.table.tableStatus.toString == TableStatus.ACTIVE.toString => ()
      case _ =>
        log.info(s"Waiting for table $name to become active ...")
        Thread.sleep(500L)
        waitForTableToBecomeActive(name)
    }
  }

}

object Dynamo {

  case class TableWrapper[A](private[Dynamo] val definition: CreateTableRequest, table: ScanamoTable[A]) {
    val name = definition.tableName
  }

  implicit class RichScanamoOps[A](val ops: ScanamoOps[A]) extends AnyVal {
    def exec()(implicit dynamo: Dynamo): A = Scanamo.apply(dynamo.client).exec(ops)
  }
}

