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
import com.amazonaws.services.ec2.{ AmazonEC2, AmazonEC2ClientBuilder }
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.amazonaws.services.securitytoken.{ AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder }
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.sns.{ AmazonSNSAsync, AmazonSNSAsyncClientBuilder, AmazonSNSClientBuilder }
import com.gu.cm.{ AwsInstanceImpl, InstanceDescriber, SysOutLogger, Configuration => CmConfiguration, Mode => CmMode }
import com.gu.googleauth.GoogleAuthConfig
import controllers._
import data.{ Dynamo, Recipes }
import event.{ ActorSystemWrapper, BakeEvent, Behaviours }
import housekeeping._
import housekeeping.utils.{ BakesRepo, PackerEC2Client }
import models.NotificationConfig
import notification.{ AmiCreatedNotifier, LambdaDistributionBucket, NotificationSender, SNS }
import org.joda.time.Duration
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import packer.{ PackerConfig, PackerRunner }
import play.api.{ BuiltInComponentsFromContext, Configuration }
import play.api.ApplicationLoader.Context
import play.api.Mode.{ Dev, Prod, Test }
import play.api.i18n.I18nComponents
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.streams.IterateeStreams
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import prism.Prism
import router.Routes
import schedule.{ BakeScheduler, ScheduledBakeRunner }
import services.{ AmiMetadataLookup, ElkLogging, Loggable, PrismAgents }

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
    with Loggable
    with AssetsComponents
    with HttpFiltersComponents {

  val awsInstance = new AwsInstanceImpl(SysOutLogger)

  val configurationMagicMode = context.environment.mode match {
    case Dev => CmMode.Dev
    case Test => CmMode.Test
    case Prod => CmMode.Prod
  }

  val identity = {
    new InstanceDescriber("amigo", configurationMagicMode, awsInstance, SysOutLogger).whoAmI
  }

  val configurationMagic: Configuration = {
    val config = CmConfiguration.fromIdentity(
      identity = identity,
      mode = configurationMagicMode
    ).load.resolve()
    log.info(s"Configuration loaded from ${config.origin().description()}")
    Configuration(config)
  }

  override lazy val configuration: Configuration = context.initialConfiguration ++ configurationMagic

  def mandatoryConfig(key: String): String = configuration.get[Option[String]](key).getOrElse(sys.error(s"Missing config key: $key"))

  val awsCreds = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("deployTools"),
    new ProfileCredentialsProvider(),
    InstanceProfileCredentialsProvider.getInstance()
  )

  val region = Regions.EU_WEST_1

  val clientConfiguration = new ClientConfiguration().
    withRetryPolicy(new RetryPolicy(
      new LoggingRetryCondition(),
      PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
      20,
      false
    ))

  // initialise logging
  val elkLoggingStream = configuration.get[Option[String]]("elk.loggingStream")
  val elkLogging = new ElkLogging(identity, awsInstance, elkLoggingStream, awsCreds, applicationLifecycle)

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

  val ec2Client: AmazonEC2 = AmazonEC2ClientBuilder.standard
    .withCredentials(awsCreds)
    .withRegion(region)
    .withClientConfiguration(clientConfiguration)
    .build()

  val amiMetadataLookup: AmiMetadataLookup = new AmiMetadataLookup(ec2Client)

  val prism = new Prism(wsClient)
  val prismAgents = new PrismAgents(prism, applicationLifecycle, actorSystem.scheduler, environment)

  // do this synchronously at startup so we can set permissions
  val accountNumbers: Seq[String] = Await.result(prism.findAllAWSAccounts(), 30 seconds).map(_.accountNumber)

  val sns: SNS = {
    val snsClient = AmazonSNSClientBuilder.standard
      .withRegion(region)
      .withCredentials(awsCreds)
      .withClientConfiguration(clientConfiguration)
      .build()
    new SNS(snsClient, identity.stage, accountNumbers)
  }

  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
    .withRegion(region)
    .withCredentials(awsCreds)
    .withClientConfiguration(clientConfiguration)
    .build()

  val anghammaradSNSClient: AmazonSNSAsync = AmazonSNSAsyncClientBuilder.standard
    .withRegion(region)
    .withCredentials(awsCreds)
    .withClientConfiguration(clientConfiguration)
    .build()

  val amigoUrl: String = configuration.get[Option[String]]("amigo.url").getOrElse(s"https://${identity.app}.gutools.co.uk")
  val anghammaradNotificationTopic: Option[String] = configuration.get[Option[String]]("anghammarad.sns.topicArn")
  val notificationConfig: Option[NotificationConfig] = anghammaradNotificationTopic.map { t =>
    NotificationConfig(amigoUrl, t, anghammaradSNSClient, identity.stage)
  }

  configuration.get[Option[String]]("aws.distributionBucket").foreach { bucketName =>
    LambdaDistributionBucket.updateBucketPolicy(s3Client, bucketName, identity.stage, accountNumbers)
  }

  val (eventsEnumerator, eventsChannel) = Concurrent.broadcast[BakeEvent]
  val eventsSource = Source.fromPublisher(IterateeStreams.enumeratorToPublisher(eventsEnumerator))
  val eventBusActorSystem = {
    val eventListeners = Map(
      "channelSender" -> Props(Behaviours.sendToChannel(eventsChannel)),
      "logWriter" -> Props(Behaviours.writeToLog),
      "dynamoWriter" -> Props(Behaviours.persistBakeEvent(notificationConfig))
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
    vpcId = configuration.get[Option[String]]("packer.vpcId"),
    subnetId = configuration.get[Option[String]]("packer.subnetId"),
    instanceProfile = configuration.get[Option[String]]("packer.instanceProfile"),
    securityGroupId = configuration.get[Option[String]]("packer.securityGroupId")
  )

  val ansibleVariables: Map[String, String] =
    Map("s3_prefix" -> configuration.get[Option[String]]("ansible.packages.s3prefix").getOrElse("")) ++
      configuration.get[Option[String]]("ansible.packages.s3bucket").map("s3_bucket" ->)

  val amigoDataBucket: Option[String] = configuration.get[Option[String]]("amigo.data.bucket")

  val packerRunner = new PackerRunner(configuration.get[Option[Int]]("packer.maxInstances").getOrElse(5))

  val scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()

  val scheduledBakeRunner: ScheduledBakeRunner = {
    val enabled = identity.stage == "PROD" // don't run scheduled bakes on dev machines
    new ScheduledBakeRunner(identity.stage, enabled, prismAgents, eventBus, ansibleVariables, amiMetadataLookup, amigoDataBucket, packerRunner)
  }
  val bakeScheduler = new BakeScheduler(scheduler, scheduledBakeRunner)

  log.info("Registering all scheduled bakes with the scheduler")
  bakeScheduler.initialise(Recipes.list())

  val bakesRepo = new BakesRepo(notificationConfig)
  val packerEC2Client = new PackerEC2Client(ec2Client, identity.stage)

  val bakeDeletionFrequencyMinutes = 1
  val houseKeepingJobs = List(
    new BakeDeletion(dynamo, awsAccount, prismAgents, sender, bakeDeletionFrequencyMinutes),
    new MarkOldUnusedBakesForDeletion(prismAgents, dynamo),
    new MarkOrphanedBakesForDeletion(prismAgents, dynamo),
    new TimeOutLongRunningBakes(bakesRepo, packerEC2Client),
    new DeleteLongRunningEC2Instances(bakesRepo, packerEC2Client)
  )

  val housekeepingScheduler = new HousekeepingScheduler(scheduler, houseKeepingJobs)
  housekeepingScheduler.initialise()

  val debugAvailable = identity.stage != "PROD"

  val rootController = new RootController(googleAuthConfig, controllerComponents)
  val baseImageController = new BaseImageController(googleAuthConfig, prismAgents, controllerComponents)
  val housekeepingController = new HousekeepingController(googleAuthConfig, controllerComponents)
  val roleController = new RoleController(googleAuthConfig, controllerComponents)
  val recipeController = new RecipeController(bakeScheduler, prismAgents, googleAuthConfig, controllerComponents, debugAvailable)
  val bakeController = new BakeController(
    identity.stage,
    eventsSource,
    prismAgents,
    googleAuthConfig,
    controllerComponents,
    ansibleVariables,
    debugAvailable,
    amiMetadataLookup,
    amigoDataBucket,
    s3Client,
    packerRunner,
    bakeDeletionFrequencyMinutes)
  val authController = new Auth(googleAuthConfig, controllerComponents)(wsClient)
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
