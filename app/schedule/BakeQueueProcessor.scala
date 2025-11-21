package schedule

import models.RecipeId
import play.api.libs.json.{Json, OFormat}
import services.Loggable
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{
  DeleteMessageRequest,
  ReceiveMessageRequest
}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

case class BakeQueueJob(recipe: RecipeId, buildNumber: Int)
object BakeQueueJob {
  implicit val format: OFormat[BakeQueueJob] = Json.format[BakeQueueJob]
}

class BakeQueueProcessor(
    sqs: SqsClient,
    bakeQueueUrl: String,
    runner: ScheduledBakeRunner
) extends Loggable {
  private val bakeTimeout = 60.minutes

  def run(): Unit = {

    while (true) {
      try {
        val rmr = ReceiveMessageRequest
          .builder()
          .queueUrl(bakeQueueUrl)
          .waitTimeSeconds(20) // max wait time
          .maxNumberOfMessages(1)
          .visibilityTimeout(bakeTimeout.toSeconds.toInt)
          .build()
        val resp = sqs.receiveMessage(rmr)

        for (message <- resp.messages().asScala) {
          val job = Json.parse(message.body()).as[BakeQueueJob]
          val completion = Await.result(
            runner.bake(job.recipe, Some(job.buildNumber)),
            bakeTimeout
          )
          completion match {
            case Some(0) =>
              val dmr = DeleteMessageRequest
                .builder()
                .queueUrl(bakeQueueUrl)
                .receiptHandle(message.receiptHandle())
                .build()
              sqs.deleteMessage(dmr)
            case Some(n) =>
              log.warn(
                s"Bake for ${job.recipe.value} ${job.buildNumber} failed with exit code $n, leaving message ${message.messageId()} on the queue."
              )
            case None =>
              log.warn(
                s"Bake for ${job.recipe.value} ${job.buildNumber} did not run, leaving message ${message.messageId()} on the queue."
              )
          }
        }
      } catch {
        case NonFatal(e) =>
          log.error("Error while processing the bake queue", e)
      }
    }

  }
}
