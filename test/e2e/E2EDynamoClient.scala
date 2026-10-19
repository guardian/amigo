package e2e

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._

private[e2e] final class E2EDynamoClient(
    tables: Map[String, List[Map[String, AttributeValue]]]
) extends DynamoDbClient {
  private def rows(name: String): List[Map[String, AttributeValue]] =
    tables.getOrElse(
      name,
      throw new IllegalArgumentException(s"Unknown E2E table: $name")
    )

  override def scan(request: ScanRequest): ScanResponse =
    ScanResponse
      .builder()
      .items(rows(request.tableName()).map(_.asJava).asJava)
      .build()

  override def getItem(request: GetItemRequest): GetItemResponse = {
    val found = rows(request.tableName()).find { row =>
      request.key().asScala.forall { case (key, value) =>
        row.get(key).contains(value)
      }
    }
    GetItemResponse
      .builder()
      .item(found.getOrElse(Map.empty[String, AttributeValue]).asJava)
      .build()
  }

  override def query(request: QueryRequest): QueryResponse = {
    require(
      request.tableName() == "amigo-E2E-bakes",
      "Only bake queries are supported by the E2E fixtures"
    )
    QueryResponse
      .builder()
      .items(rows(request.tableName()).map(_.asJava).asJava)
      .build()
  }

  override def putItem(request: PutItemRequest): PutItemResponse =
    throw new UnsupportedOperationException(
      "DynamoDB.putItem is disabled in E2E"
    )

  override def serviceName(): String = DynamoDbClient.SERVICE_NAME

  override def close(): Unit = ()
}
