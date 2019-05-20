package components

import akka.stream.scaladsl.Source
import akka.typed._
import com.amazonaws.{ AmazonClientException, AmazonWebServiceRequest, ClientConfiguration }
import com.amazonaws.auth.{ AWSCredentialsProviderChain, InstanceProfileCredentialsProvider }
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.retry.{ PredefinedRetryPolicies, RetryPolicy }
import com.amazonaws.retry.PredefinedRetryPolicies.SDKDefaultRetryCondition
import com.amazonaws.services.dynamodbv2.{ AmazonDynamoDB, AmazonDynamoDBClient }
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.amazonaws.services.securitytoken.{ AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder }
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.gu.cm.{ ConfigurationLoader, Identity }
import com.gu.googleauth.GoogleAuthConfig
import controllers._
import data.{ Dynamo, Recipes }
import event.{ ActorSystemWrapper, BakeEvent, Behaviours }
import housekeeping.{ BakeDeletion, HousekeepingScheduler, MarkOldUnusedBakesForDeletion, MarkOrphanedBakesForDeletion }
import notification.{ AmiCreatedNotifier, LambdaDistributionBucket, NotificationSender, SNS }
import org.joda.time.Duration
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import packer.PackerConfig
import play.api.{ BuiltInComponentsFromContext, Configuration, Logger }
import play.api.ApplicationLoader.Context
import play.api.i18n.I18nComponents
import play.api.libs.iteratee.Concurrent
import play.api.libs.streams.Streams
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import prism.Prism
import router.Routes
import schedule.{ BakeScheduler, ScheduledBakeRunner }
import services.{ ElkLogging, Loggable, PrismAgents }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class LoggingRetryCondition extends SDKDefaultRetryCondition with Loggable {
  private def exceptionInfo(e: Throwable): String = {
    s"${e.getClass.getName} ${e.getMessage} Cause: ${Option(e.getCause).map(e => exceptionInfo(e))}"
  }

  override def shouldRetry(originalRequest: AmazonWebServiceRequest, exception: AmazonClientException, retriesAttempted: Int): Boolean = {
    val willRetry = super.shouldRetry(originalRequest, exception, retriesAttempted)
    if (willRetry) {
      log.warn(s"AWS SDK retry $retriesAttempted: ${Option(originalRequest).map(_.getClass.getName)} threw ${exceptionInfo(exception)}")
    } else {
      log.warn(s"Encountered fatal exception during AWS API call", exception)
      Option(exception.getCause).foreach(t => log.warn(s"Cause of fatal exception", t))
    }
    willRetry
  }
}

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with I18nComponents
    with Loggable {

  val identity = {
    import com.gu.cm.PlayImplicits._
    Identity.whoAmI("amigo", context.environment.mode)
  }
  override lazy val configuration: Configuration = context.initialConfiguration ++ ConfigurationLoader.playConfig(identity, context.environment.mode)

  def mandatoryConfig(key: String): String = configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  implicit val executionContext = actorSystem.dispatcher

  val awsCreds = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("deployTools"),
    new ProfileCredentialsProvider(),
    InstanceProfileCredentialsProvider.getInstance()
  )
  val region = configuration.getString("aws.region").map(Regions.fromName).getOrElse(Regions.EU_WEST_1)
  val clientConfiguration = new ClientConfiguration().
    withRetryPolicy(new RetryPolicy(
      new LoggingRetryCondition(),
      PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
      20,
      false
    ))

  // initialise logging
  val elkLoggingStream = configuration.getString("elk.loggingStream")
  val elkLogging = new ElkLogging(identity, elkLoggingStream, awsCreds, applicationLifecycle)

  implicit val dynamo = {
    val dynamoClient: AmazonDynamoDB = AmazonDynamoDBClient.builder()
      .withCredentials(awsCreds)
      .withRegion(region)
      .withClientConfiguration(clientConfiguration)
      .build()
    new Dynamo(dynamoClient, identity.stage)
  }
  dynamo.initTables()

  val awsAccount = {
    val stsClient: AWSSecurityTokenService = AWSSecurityTokenServiceClientBuilder.standard
      .withCredentials(awsCreds)
      .withRegion(region)
      .withClientConfiguration(clientConfiguration)
      .build()
    val result = stsClient.getCallerIdentity(new GetCallerIdentityRequest())
    val amigoAwsAccount = result.getAccount
    amigoAwsAccount
  }

  val prism = new Prism(wsClient)
  val prismAgents = new PrismAgents(prism, applicationLifecycle, actorSystem.scheduler, environment)

  // do this synchronously at startup so we can set permissions
  val accountNumbers: Seq[String] = Await.result(prism.findAllAWSAccounts().asFuture, 30 seconds).right.get.map(_.accountNumber)

  val sns: SNS = {
    val snsClient = AmazonSNSClientBuilder.standard
      .withRegion(region)
      .withCredentials(awsCreds)
      .withClientConfiguration(clientConfiguration)
      .build()
    new SNS(snsClient, identity.stage, accountNumbers)
  }

  configuration.getString("aws.distributionBucket").foreach { bucketName =>
    val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
      .withRegion(region)
      .withCredentials(awsCreds)
      .withClientConfiguration(clientConfiguration)
      .build()
    LambdaDistributionBucket.updateBucketPolicy(s3Client, bucketName, identity.stage, accountNumbers)
  }

  val (eventsEnumerator, eventsChannel) = Concurrent.broadcast[BakeEvent]
  val eventsSource = Source.fromPublisher(Streams.enumeratorToPublisher(eventsEnumerator))
  val eventBusActorSystem = {
    val eventListeners = Map(
      "channelSender" -> Props(Behaviours.sendToChannel(eventsChannel)),
      "logWriter" -> Props(Behaviours.writeToLog),
      "dynamoWriter" -> Props(Behaviours.writeToDynamo)
    )
    ActorSystem[BakeEvent]("EventBus", Props(Behaviours.guardian(eventListeners)))
  }
  implicit val eventBus = new ActorSystemWrapper(eventBusActorSystem)

  val sender: NotificationSender = new NotificationSender(sns, identity.region, identity.stage)
  val completedBakeNotifier: AmiCreatedNotifier = new AmiCreatedNotifier(eventsSource, sender.sendTopicMessage)

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = Some("guardian.co.uk"),
    maxAuthAge = Some(Duration.standardDays(90)),
    enforceValidity = true
  )

  implicit val packerConfig = PackerConfig(
    stage = identity.stage,
    vpcId = configuration.getString("packer.vpcId"),
    subnetId = configuration.getString("packer.subnetId"),
    instanceProfile = configuration.getString("packer.instanceProfile")
  )

  val ansibleVariables: Map[String, String] =
    Map("s3_prefix" -> configuration.getString("ansible.packages.s3prefix").getOrElse("")) ++
      configuration.getString("ansible.packages.s3bucket").map("s3_bucket" ->)

  val scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()

  val scheduledBakeRunner = {
    val enabled = identity.stage == "PROD" // don't run scheduled bakes on dev machines
    new ScheduledBakeRunner(enabled, prismAgents, eventBus, ansibleVariables)
  }
  val bakeScheduler = new BakeScheduler(scheduler, scheduledBakeRunner)

  log.info("Registering all scheduled bakes with the scheduler")
  bakeScheduler.initialise(Recipes.list())

  val bakeDeletionHousekeeping = new BakeDeletion(dynamo, awsAccount, prismAgents, sender)
  val markOldUnusedBakesForDeletion = new MarkOldUnusedBakesForDeletion(prismAgents, dynamo)
  val markOrphanedBakesForDeletion = new MarkOrphanedBakesForDeletion(prismAgents, dynamo)

  val housekeepingScheduler = new HousekeepingScheduler(scheduler, List(bakeDeletionHousekeeping, markOldUnusedBakesForDeletion, markOrphanedBakesForDeletion))
  //housekeepingScheduler.initialise()

  val debugAvailable = identity.stage != "PROD"

  val rootController = new RootController(googleAuthConfig)
  val baseImageController = new BaseImageController(googleAuthConfig, messagesApi)
  val housekeepingController = new HousekeepingController(googleAuthConfig)
  val roleController = new RoleController(googleAuthConfig)
  val recipeController = new RecipeController(bakeScheduler, prismAgents, googleAuthConfig, messagesApi, debugAvailable)
  val bakeController = new BakeController(eventsSource, prismAgents, googleAuthConfig, messagesApi, ansibleVariables, debugAvailable)
  val authController = new Auth(googleAuthConfig)(wsClient)
  val assets = new controllers.Assets(httpErrorHandler)
  lazy val router: Router = new Routes(
    httpErrorHandler,
    rootController,
    baseImageController,
    housekeepingController,
    roleController,
    recipeController,
    bakeController,
    authController,
    assets)
}
