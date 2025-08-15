package schedule

import data.{Dynamo, Recipes}
import org.apache.pekko.actor.Scheduler
import play.api.libs.json.Json
import services.Loggable
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import java.time.format.TextStyle
import java.time.{
  Duration,
  LocalDate,
  LocalDateTime,
  LocalTime,
  ZoneId,
  ZonedDateTime
}
import java.util.Locale
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

object BakeQueueScheduler extends Loggable {
  private val London = ZoneId.of("Europe/London")

  // FIXME currently relies on there usually only being one instance of Amigo to avoid duplicating bakes unnecessarily...
  // Needs improving. Lambda on a schedule makes some sense, but then that's another service to deploy, with a dependency
  // chain to keep up-to-date, configuring an AWS SDK to scan the table and write to the queue, etc. It is much easier to
  // have an extra ≤20 lines of Scala running on the instance we already have...
  // Maybe we have the actual bake runner skip a scheduled bake if one has run in the past 24h?
  def schedule(
      scheduler: Scheduler,
      sqsClient: SqsClient,
      bakeQueueUrl: String
  )(implicit ec: ExecutionContext, dynamo: Dynamo): Unit = {
    val nextMidnight =
      LocalDateTime.of(LocalDate.now(London), LocalTime.MIDNIGHT).atZone(London)
    val timeToNextMidnight = Duration
      .between(ZonedDateTime.now(London), nextMidnight)
      .toScala
      .plus(5.minutes)

    // FIXME for testing, running immediately every 15 mins
    scheduler.scheduleAtFixedRate(
      initialDelay = 0.seconds,
      interval = 15.minutes
    ) { () =>
      // scheduler.scheduleAtFixedRate(initialDelay = timeToNextMidnight, interval = 1.day) { () =>
      val allRecipes = Recipes.list()
      val today = ZonedDateTime
        .now(London)
        .getDayOfWeek
        .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
      val todaysRecipes = allRecipes.filter(_.bakeDay.contains(today)).map(_.id)

      for {
        recipe <- todaysRecipes
      } {
        Recipes.incrementAndGetBuildNumber(recipe) match {
          case Some(buildNo) =>
            val bakeJob = BakeQueueJob(recipe, buildNo)
            val smr = SendMessageRequest
              .builder()
              .queueUrl(bakeQueueUrl)
              .messageBody(Json.toJson(bakeJob).toString())
              .build()
            sqsClient.sendMessage(smr)
          case None =>
            log.warn(
              s"Failed to queue bake job for recipe $recipe - could not determine build number"
            )
        }
      }
    }
  }
}
