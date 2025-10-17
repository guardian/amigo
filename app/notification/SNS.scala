package notification

import services.Loggable
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object SNS extends Loggable {
  def listAwsResource[T](
      request: Option[String] => (List[T], Option[String])
  ): List[T] = {
    @tailrec
    def listAwsResourceRec(
        soFar: List[T],
        nextToken: Option[String]
    ): List[T] = {
      nextToken match {
        case None => soFar
        case Some(next) =>
          val (more, nextNext) = request(Some(next))
          listAwsResourceRec(soFar ::: more, nextNext)
      }
    }
    val (initialResults, initialNext) = request(None)
    listAwsResourceRec(initialResults, initialNext)
  }

  @tailrec
  private def waitForTopicToBecomeAvailable(
      arn: String
  )(implicit client: SnsClient): Unit = {
    if (!listTopicArns.exists(arn ==)) {
      log.info(s"Waiting for topic $arn to become available ...")
      Thread.sleep(500L)
      waitForTopicToBecomeAvailable(arn)
    }
  }

  def listTopicArns(implicit client: SnsClient): List[String] = SNS
    .listAwsResource[Topic] { nextToken =>
      val requestBuilder = ListTopicsRequest.builder()
      nextToken.foreach(requestBuilder.nextToken)
      val result = client.listTopics(requestBuilder.build())
      result.topics().asScala.toList -> Option(result.nextToken())
    }
    .map(_.topicArn())

  def createTopic(topicName: String)(implicit client: SnsClient): String = {
    val request = CreateTopicRequest.builder().name(topicName).build()
    val result = client.createTopic(request)
    val topicArn = result.topicArn()
    SNS.waitForTopicToBecomeAvailable(topicArn)
    topicArn
  }

  def updatePermissions(topicArn: String, accounts: Seq[String])(implicit
      client: SnsClient
  ): Unit = {
    val removeRequest = RemovePermissionRequest
      .builder()
      .topicArn(topicArn)
      .label("amigo_lambda_subs")
      .build()
    client.removePermission(removeRequest)
    val addRequest = AddPermissionRequest
      .builder()
      .topicArn(topicArn)
      .awsAccountIds(accounts: _*)
      .actionNames("Subscribe", "ListSubscriptionsByTopic", "Receive")
      .label("amigo_lambda_subs")
      .build()
    client.addPermission(addRequest)
  }

  def findOrCreateTopic(topicName: String, accountNumbers: Seq[String])(implicit
      client: SnsClient
  ): String = {
    val topicArn = listTopicArns.find(_.endsWith(s":$topicName")) match {
      case None      => createTopic(topicName)
      case Some(arn) => arn
    }
    updatePermissions(topicArn, accountNumbers)
    topicArn
  }
}

class SNS(sns: SnsClient, stage: String, accountNumbers: Seq[String])(implicit
    exec: ExecutionContext
) {
  implicit val client: SnsClient = sns
  val topicName: String = s"amigo-$stage-notify"
  val topicArn: String = SNS.findOrCreateTopic(topicName, accountNumbers)

  val housekeepingTopicName: String = s"amigo-$stage-housekeeping-notify"
  val housekeepingTopicArn: String =
    SNS.findOrCreateTopic(housekeepingTopicName, accountNumbers)
}
