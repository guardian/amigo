package data

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import com.gu.cm.Identity
import com.gu.scanamo.Scanamo
import com.gu.scanamo.ops._
import play.api.Logger

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.{ Success, Try }

class Dynamo(val client: AmazonDynamoDB, identity: Identity) {
  import Dynamo._

  def exec[T](ops: ScanamoOps[T]): T = Scanamo.exec(client)(ops)

  object Tables {

    val baseImages = new Table(
      new CreateTableRequest()
        .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
        .withAttributeDefinitions(new AttributeDefinition("id", ScalarAttributeType.S))
        .withTableName(tableName("base-images"))
        .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
    )

    val recipes = new Table(
      new CreateTableRequest()
        .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
        .withAttributeDefinitions(new AttributeDefinition("id", ScalarAttributeType.S))
        .withTableName(tableName("recipes"))
        .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
    )

    val bakes = new Table(
      new CreateTableRequest()
        .withKeySchema(new KeySchemaElement("recipeId", KeyType.HASH), new KeySchemaElement("buildNumber", KeyType.RANGE))
        .withAttributeDefinitions(new AttributeDefinition("recipeId", ScalarAttributeType.S), new AttributeDefinition("buildNumber", ScalarAttributeType.N))
        .withTableName(tableName("bakes"))
        .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
    )

    val bakeLogs = new Table(
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

  private def tableName(suffix: String) = Dynamo.tableName(identity, suffix)

  private def createTableIfDoesNotExist(table: Table): Unit = {
    if (Try(client.describeTable(table.name)).isFailure) {
      Logger.info(s"Creating Dynamo table ${table.name} ...")
      client.createTable(table.definition)
      waitForTableToBecomeActive(table.name)
    } else {
      Logger.info(s"Found Dynamo table ${table.name}")
    }
  }

  @tailrec
  private def waitForTableToBecomeActive(name: String): Unit = {
    Try(Option(client.describeTable(name).getTable)).toOption.flatten match {
      case Some(table) if table.getTableStatus == TableStatus.ACTIVE.toString => ()
      case _ =>
        Logger.info(s"Waiting for table $name to become active ...")
        Thread.sleep(500L)
        waitForTableToBecomeActive(name)
    }
  }

}

object Dynamo {

  def tableName(identity: Identity, suffix: String) = s"${identity.app}-${identity.stage}-$suffix"

  class Table(private[Dynamo] val definition: CreateTableRequest) {
    val name: String = definition.getTableName

    val hashKey: String = definition.getKeySchema.asScala
      .collectFirst { case x if x.getKeyType == KeyType.HASH.toString => x.getAttributeName }
      .getOrElse(sys.error(s"Table definition for table $name does not specify the hash key"))

    val rangeKey: Option[String] = definition.getKeySchema.asScala
      .collectFirst { case x if x.getKeyType == KeyType.RANGE.toString => x.getAttributeName }
  }

}

