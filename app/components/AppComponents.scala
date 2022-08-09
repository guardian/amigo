package components

import akka.actor.typed.ActorSystem
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.retry.PredefinedRetryPolicies.SDKDefaultRetryCondition
import com.amazonaws.retry.{PredefinedRetryPolicies, RetryPolicy}
import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}
import com.amazonaws.services.sns.{AmazonSNSAsync, AmazonSNSAsyncClientBuilder, AmazonSNSClientBuilder}
import com.amazonaws.{AmazonClientException, AmazonWebServiceRequest, ClientConfiguration}
import com.gu.googleauth.{AntiForgeryChecker, AuthAction, GoogleAuthConfig}
import com.gu.play.secretrotation.aws.parameterstore.{AwsSdkV2, SecretSupplier}
import com.gu.play.secretrotation.{RotatingSecretComponents, SnapshotProvider, TransitionTiming}
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import controllers._
import data.{Dynamo, Recipes}
import event.{ActorSystemWrapper, BakeEvent, Behaviours}
import housekeeping._
import housekeeping.utils.{BakesRepo, PackerEC2Client}
import models.NotificationConfig
import notification.{LambdaDistributionBucket, NotificationSender, SNS}
import org.joda.time.Duration
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import packer.{PackerConfig, PackerRunner}
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.i18n.I18nComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import play.filters.csp.CSPComponents
import prism.Prism
import router.Routes
import schedule.{BakeScheduler, ScheduledBakeRunner}
import services.{AmiMetadataLookup, ElkLogging, Loggable, PrismData}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProvider => AwsCredentialsProviderV2}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ssm.SsmClient

import java.time.Duration.{ofHours, ofMinutes}
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

class AppComponents(
  context: Context,
  identity: AppIdentity,
  awsCredsForV1: AWSCredentialsProvider,
  awsCredsForV2: AwsCredentialsProviderV2
) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with I18nComponents
  with Loggable
  with AssetsComponents
  with HttpFiltersComponents
  with RotatingSecretComponents
  with CSPComponents {

  val stage = identity match {
    case DevIdentity(_) => "DEV"
    case AwsIdentity(_, _, stage, _) => stage
  }

  def mandatoryConfig(key: String): String = configuration.get[Option[String]](key).getOrElse(sys.error(s"Missing config key: $key"))

  val region = Regions.EU_WEST_1

  val clientConfiguration = new ClientConfiguration().
    withRetryPolicy(new RetryPolicy(
      new LoggingRetryCondition(),
      PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
      20,
      false))

  val secretStateSupplier: SnapshotProvider = {
    new SecretSupplier(
      TransitionTiming(usageDelay = ofMinutes(3), overlapDuration = ofHours(2)),
      s"/$stage/deploy/amigo/play.http.secret.key",
      AwsSdkV2(SsmClient.builder
        .credentialsProvider(awsCredsForV2)
        .region(Region.of(region.getName))
        .build()))
  }

  // initialise logging
  val elkLoggingStream = configuration.get[Option[String]]("elk.loggingStream")
  val elkLogging = new ElkLogging(identity, elkLoggingStream, awsCredsForV2)

  implicit val dynamo = {
    val dynamoClient: DynamoDbClient = DynamoDbClient.builder()
      .credentialsProvider(awsCredsForV2)
      .region(Region.of(region.getName))
      .build()
    new Dynamo(dynamoClient, stage)
  }
  dynamo.initTables()

  val awsAccount = {
    val stsClient: AWSSecurityTokenService = AWSSecurityTokenServiceClientBuilder.standard
      .withCredentials(awsCredsForV1)
      .withRegion(region)
      .withClientConfiguration(clientConfiguration)
      .build()
    val result = stsClient.getCallerIdentity(new GetCallerIdentityRequest())
    val amigoAwsAccount = result.getAccount
    amigoAwsAccount
  }

  val ec2Client: AmazonEC2 = AmazonEC2ClientBuilder.standard
    .withCredentials(awsCredsForV1)
    .withRegion(region)
    .withClientConfiguration(clientConfiguration)
    .build()

  val amiMetadataLookup: AmiMetadataLookup = new AmiMetadataLookup(ec2Client)

  val prism = new Prism(wsClient)
  val prismAgents = new PrismData(prism, applicationLifecycle, actorSystem.scheduler, environment)

  // do this synchronously at startup so we can set permissions
  val accountNumbers: Seq[String] = Await.result(prism.findAllAWSAccounts(), 30 seconds).map(_.accountNumber)

  val sns: SNS = {
    val snsClient = AmazonSNSClientBuilder.standard
      .withRegion(region)
      .withCredentials(awsCredsForV1)
      .withClientConfiguration(clientConfiguration)
      .build()
    new SNS(snsClient, stage, accountNumbers)
  }

  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
    .withRegion(region)
    .withCredentials(awsCredsForV1)
    .withClientConfiguration(clientConfiguration)
    .build()

  val anghammaradSNSClient: AmazonSNSAsync = AmazonSNSAsyncClientBuilder.standard
    .withRegion(region)
    .withCredentials(awsCredsForV1)
    .withClientConfiguration(clientConfiguration)
    .build()

  val amigoUrl: String = configuration.get[Option[String]]("amigo.url").getOrElse(s"https://amigo.gutools.co.uk")
  val anghammaradNotificationTopic: Option[String] = configuration.get[Option[String]]("anghammarad.sns.topicArn")
  val notificationConfig: Option[NotificationConfig] = anghammaradNotificationTopic.map { t =>
    NotificationConfig(amigoUrl, t, anghammaradSNSClient, stage)
  }

  configuration.get[Option[String]]("aws.distributionBucket").foreach { bucketName =>
    LambdaDistributionBucket.updateBucketPolicy(s3Client, bucketName, stage, accountNumbers)
  }

  val sender: NotificationSender = new NotificationSender(sns, region.getName, stage)

  val eventBusActorSystem: ActorSystem[BakeEvent] = {
    val eventListeners = Map(
      "logWriter" -> Behaviours.writeToLog,
      "dynamoWriter" -> Behaviours.persistBakeEvent(notificationConfig),
      "snsWriter" -> Behaviours.sendAmiCreatedNotification(sender.sendTopicMessage))
    ActorSystem[BakeEvent](Behaviours.guardian(eventListeners), "EventBus")
  }

  implicit val eventBus = new ActorSystemWrapper(eventBusActorSystem)

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domains = List("guardian.co.uk"),
    maxAuthAge = Some(Duration.standardDays(90)),
    enforceValidity = true,
    antiForgeryChecker = AntiForgeryChecker(secretStateSupplier))

  implicit val packerConfig = PackerConfig(
    stage = stage,
    vpcId = configuration.get[Option[String]]("packer.vpcId"),
    subnetId = configuration.get[Option[String]]("packer.subnetId"),
    instanceProfile = configuration.get[Option[String]]("packer.instanceProfile"),
    securityGroupId = configuration.get[Option[String]]("packer.securityGroupId"))

  val ansibleVariables: Map[String, String] =
    Map("s3_prefix" -> configuration.get[String]("ansible.packages.s3prefix")) ++
      configuration.get[Option[String]]("ansible.packages.s3bucket").map("s3_bucket" ->)

  val amigoDataBucket: Option[String] = configuration.get[Option[String]]("amigo.data.bucket")

  val packerRunner = new PackerRunner(configuration.get[Int]("packer.maxInstances"))

  val quartzScheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()

  val scheduledBakeRunner: ScheduledBakeRunner = {
    val enabled = stage == "PROD" // don't run scheduled bakes on dev machines
    new ScheduledBakeRunner(stage, enabled, prismAgents, eventBus, ansibleVariables, amiMetadataLookup, amigoDataBucket, packerRunner)
  }
  val bakeScheduler = new BakeScheduler(quartzScheduler, scheduledBakeRunner)

  log.info("Registering all scheduled bakes with the scheduler")
  bakeScheduler.initialise(Recipes.list())

  val bakesRepo = new BakesRepo(notificationConfig)
  val packerEC2Client = new PackerEC2Client(ec2Client, stage)

  val bakeDeletionFrequencyMinutes = 1
  val houseKeepingJobs = List(
    new BakeDeletion(dynamo, awsAccount, prismAgents, sender, bakeDeletionFrequencyMinutes),
    new MarkOldUnusedBakesForDeletion(prismAgents, dynamo),
    new MarkOrphanedBakesForDeletion(prismAgents, dynamo),
    new TimeOutLongRunningBakes(bakesRepo, packerEC2Client),
    new DeleteLongRunningEC2Instances(bakesRepo, packerEC2Client))

  val housekeepingScheduler = new HousekeepingScheduler(quartzScheduler, houseKeepingJobs)
  housekeepingScheduler.initialise()

  val debugAvailable = stage != "PROD"

  /**
   * Play 2.8's default is Seq(csrfFilter, securityHeadersFilter, allowedHostsFilter).
   * The allowedHostsFilter is removed here as it causes healthchecks to fail.
   * This service is not accessible on the public internet.
   *
   * We also enable cspFilter, as per https://www.playframework.com/documentation/2.8.x/CspFilter#Enabling-Through-Compile-Time
   */
  override def httpFilters: Seq[EssentialFilter] = Seq(csrfFilter, securityHeadersFilter, cspFilter)

  val authAction = new AuthAction[AnyContent](googleAuthConfig, routes.Login.loginAction(), controllerComponents.parsers.default)(executionContext)

  val rootController = new RootController(authAction, controllerComponents)
  val baseImageController = new BaseImageController(authAction, prismAgents, controllerComponents)
  val housekeepingController = new HousekeepingController(authAction, controllerComponents)
  val roleController = new RoleController(authAction, controllerComponents)
  val recipeController = new RecipeController(authAction, bakeScheduler, prismAgents, controllerComponents, debugAvailable)
  val bakeController = new BakeController(
    authAction,
    stage,
    prismAgents,
    controllerComponents,
    ansibleVariables,
    debugAvailable,
    amiMetadataLookup,
    amigoDataBucket,
    s3Client,
    packerRunner,
    bakeDeletionFrequencyMinutes)
  val loginController = new Login(googleAuthConfig, wsClient, controllerComponents)
  lazy val router: Router = new Routes(
    httpErrorHandler,
    rootController,
    baseImageController,
    housekeepingController,
    roleController,
    recipeController,
    bakeController,
    loginController,
    assets)
}
