package services

import ch.qos.logback.classic.{ AsyncAppender, Logger, LoggerContext }
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter
import com.amazonaws.auth.AWSCredentialsProvider
import com.gu.{ AppIdentity, AwsIdentity, DevIdentity }
import com.gu.logback.appender.kinesis.KinesisAppender
import net.logstash.logback.layout.LogstashLayout
import org.slf4j.{ LoggerFactory, Logger => SLFLogger }
import play.api.libs.json.Json
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils
import amigo.BuildInfo

import scala.util.{ Success, Try }
import scala.util.control.NonFatal

class ElkLogging(appIdentity: AppIdentity, loggingStreamName: Option[String], awsCredentialsProvider: AWSCredentialsProvider) extends Loggable {

  val identity = appIdentity match {
    case DevIdentity(_) => None
    case awsIdentity @ AwsIdentity(_, _, _, _) => Some(awsIdentity)
  }

  val instanceId = identity.flatMap { _ =>
    Try(Option(EC2MetadataUtils.getInstanceId)) match {
      case Success(Some(instanceId)) => Some(instanceId)
      case _ => None
    }
  }

  val region = identity.map(_.region).getOrElse("eu-west-1")

  def getContextTags: Map[String, String] = {
    val effective = Map(
      "app" -> identity.map(_.app).getOrElse(""),
      "stage" -> identity.map(_.stage).getOrElse("DEV"),
      "stack" -> identity.map(_.stack).getOrElse("dev-stack"),
      "region" -> region,
      "buildNumber" -> BuildInfo.buildNumber,
      "instanceId" -> instanceId.getOrElse("unknown")
    )
    log.info(s"Logging with context map: $effective")
    effective
  }

  // initialise immediately, but ensure we don't blow anything up if we fail
  try {
    init()
  } catch {
    case NonFatal(e) => log.error("Failed to initialise log shipping", e)
  }

  def makeCustomFields(customFields: Map[String, String]): String = {
    Json.stringify(Json.toJson(customFields))
  }

  private def makeLayout(customFields: String) = {
    val l = new LogstashLayout()
    l.setCustomFields(customFields)
    l
  }

  private def makeKinesisAppender(layout: LogstashLayout, context: LoggerContext, streamName: String, bufferSize: Int): KinesisAppender[ILoggingEvent] = {
    val a = new KinesisAppender[ILoggingEvent]()
    a.setStreamName(streamName)
    a.setRegion(region)
    a.setCredentialsProvider(awsCredentialsProvider)
    a.setBufferSize(bufferSize)

    a.setContext(context)
    a.setLayout(layout)

    layout.start()
    a.start()
    a
  }

  private def wrapWithAsyncAppender(context: LoggerContext, appender: Appender[ILoggingEvent], bufferSize: Int): AsyncAppender = {
    val a = new AsyncAppender()
    a.addAppender(appender)
    a.setNeverBlock(true)
    a.setQueueSize(bufferSize)
    a.setIncludeCallerData(true)
    a.setContext(context)
    a.start()
    a
  }

  // assume SLF4J is bound to logback in the current environment
  private def getLoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  private def getRootLogger = LoggerFactory.getLogger(SLFLogger.ROOT_LOGGER_NAME).asInstanceOf[Logger]

  def init() {
    val maybeStreamName = loggingStreamName

    if (maybeStreamName.isEmpty) log.info("Not configuring log shipping as stream not configured")

    val bufferSize = 1000

    maybeStreamName.foreach { streamName =>
      log.info("Configuring logging to ship to ELK")

      try {
        val layout = makeLayout(makeCustomFields(getContextTags))
        val appender = makeKinesisAppender(layout, getLoggerContext, streamName, bufferSize)
        val asyncAppender = wrapWithAsyncAppender(getLoggerContext, appender, bufferSize)
        val rootLogger = getRootLogger
        rootLogger.addAppender(asyncAppender)
      } catch {
        case e: JoranException => // ignore, errors will be printed below
      }

      StatusPrinter.printInCaseOfErrorsOrWarnings(getLoggerContext)
      log.info("Log shipping configuration completed")
    }
  }
}
