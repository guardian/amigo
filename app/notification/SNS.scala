package notification

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.{ CreateTopicRequest, ListTopicsRequest, Topic }
import play.api.Logger

import scala.annotation.tailrec
import scala.collection.JavaConverters._
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
}

class SNS(val client: AmazonSNS, stage: String) {
  val topicName: String = s"amigo-$stage-notify"
  val topicArn: String = listTopicArns.find(_.endsWith(s":$topicName")) match {
    case None =>
      val result = client.createTopic(new CreateTopicRequest().withName(topicName))
      val topicArn = result.getTopicArn
      waitForTopicToBecomeAvailable(topicArn)
      topicArn
    case Some(arn) => arn
  }

  @tailrec
  private def waitForTopicToBecomeAvailable(arn: String): Unit = {
    if (!listTopicArns.exists(arn ==)) {
      Logger.info(s"Waiting for topic $arn to become available ...")
      Thread.sleep(500L)
      waitForTopicToBecomeAvailable(arn)
    }
  }

  def listTopicArns: List[String] = SNS.listAwsResource[Topic] { nextToken =>
    val result = client.listTopics(new ListTopicsRequest().withNextToken(nextToken.orNull))
    result.getTopics.asScala.toList -> Option(result.getNextToken)
  }.map(_.getTopicArn)
}
