package schedule

import models.RecipeId
import play.api.libs.json.{Json, OFormat}
import services.Loggable
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

case class BakeQueueJob(recipe: RecipeId)
object BakeQueueJob {
  implicit val format: OFormat[BakeQueueJob] = Json.format[BakeQueueJob]
}

class BakeQueueProcessor(
    sqs: SqsClient,
    bakeQueueUrl: String,
    runner: ScheduledBakeRunner
) extends Loggable {
  def run(): Unit = {

    while (true) {
      try {
        val rmr = ReceiveMessageRequest
          .builder()
          .queueUrl(bakeQueueUrl)
          .waitTimeSeconds(30)
          .maxNumberOfMessages(1)
          .build()
        val resp = sqs.receiveMessage(rmr)

        for (message <- resp.messages().asScala) {
          val job = Json.toJson(message.body()).as[BakeQueueJob]
          runner.bake(job.recipe)
        }
      } catch {
        case NonFatal(e) =>
          log.error("Error while processing the bake queue", e)
      }
    }

  }
}
