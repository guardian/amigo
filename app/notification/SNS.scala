package notification

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model._
import play.api.Logger

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object SNS {
  def listAwsResource[T](request: Option[String] => (List[T], Option[String])): List[T] = {
    @tailrec
    def listAwsResourceRec(soFar: List[T], nextToken: Option[String]): List[T] = {
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
  private def waitForTopicToBecomeAvailable(arn: String)(implicit client: AmazonSNS): Unit = {
    if (!listTopicArns.exists(arn ==)) {
      Logger.info(s"Waiting for topic $arn to become available ...")
      Thread.sleep(500L)
      waitForTopicToBecomeAvailable(arn)
    }
  }

  def listTopicArns(implicit client: AmazonSNS): List[String] = SNS.listAwsResource[Topic] { nextToken =>
    val result = client.listTopics(new ListTopicsRequest().withNextToken(nextToken.orNull))
    result.getTopics.asScala.toList -> Option(result.getNextToken)
  }.map(_.getTopicArn)

  def createTopic(topicName: String)(implicit client: AmazonSNS): String = {
    val result = client.createTopic(new CreateTopicRequest().withName(topicName))
    val topicArn = result.getTopicArn
    SNS.waitForTopicToBecomeAvailable(topicArn)
    topicArn
  }

  def updatePermissions(topicArn: String, accounts: Seq[String])(implicit client: AmazonSNS): Unit = {
    val removeRequest = new RemovePermissionRequest()
      .withTopicArn(topicArn)
      .withLabel("amigo_lambda_subs")
    client.removePermission(removeRequest)
    val addRequest = new AddPermissionRequest()
      .withTopicArn(topicArn)
      .withAWSAccountIds(accounts.asJava)
      .withActionNames("Subscribe", "ListSubscriptionsByTopic", "Receive")
      .withLabel("amigo_lambda_subs")
    client.addPermission(addRequest)
  }

  def findOrCreateTopic(topicName: String, accountNumbers: Seq[String])(implicit client: AmazonSNS): String = {
    val topicArn = listTopicArns.find(_.endsWith(s":$topicName")) match {
      case None => createTopic(topicName)
      case Some(arn) => arn
    }
    updatePermissions(topicArn, accountNumbers)
    topicArn
  }
}

class SNS(sns: AmazonSNS, stage: String, accountNumbers: Seq[String])(implicit exec: ExecutionContext) {
  implicit val client: AmazonSNS = sns
  val topicName: String = s"amigo-$stage-notify"
  val topicArn: String = SNS.findOrCreateTopic(topicName, accountNumbers)

  val housekeepingTopicName: String = s"amigo-$stage-housekeeping-notify"
  val housekeepingTopicArn: String = SNS.findOrCreateTopic(housekeepingTopicName, accountNumbers)
}
