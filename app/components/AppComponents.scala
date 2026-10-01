package components

import authentication.{AuthAction, IdentityProvider}
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest
import com.gu.play.secretrotation.aws.parameterstore.{AwsSdkV2, SecretSupplier}
import com.gu.play.secretrotation.{
  RotatingSecretComponents,
  SnapshotProvider,
  TransitionTiming
}
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import config.Config.mandatoryConfig
import controllers._
import data.{Dynamo, Recipes}
import event.{ActorSystemWrapper, BakeEvent, Behaviours}
import housekeeping._
import housekeeping.utils.{BakesRepo, PackerEC2Client}
import models.NotificationConfig
import notification.{LambdaDistributionBucket, NotificationSender, SNS}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.{ActorSystem => UntypedActorSystem}
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
import services.{AmiMetadataLookup, Loggable, PrismData}
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  InstanceProfileCredentialsProvider,
  ProfileCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.{SnsAsyncClient, SnsClient}
import software.amazon.awssdk.services.ssm.SsmClient

import java.time.Clock
import java.time.Duration.{ofHours, ofMinutes}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class AppComponents(
    context: Context,
    identity: AppIdentity,
    awsCredentials: AwsCredentialsProviderChain
) extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with I18nComponents
    with Loggable
    with AssetsComponents
    with HttpFiltersComponents
    with RotatingSecretComponents
    with CSPComponents {

  val stage = identity match {
    case DevIdentity(_)              => "DEV"
    case AwsIdentity(_, _, stage, _) => stage
  }

  val region = Region.EU_WEST_1

  val secretStateSupplier: SnapshotProvider = {
    new SecretSupplier(
      TransitionTiming(usageDelay = ofMinutes(3), overlapDuration = ofHours(2)),
      s"/$stage/deploy/amigo/play.http.secret.key",
      AwsSdkV2(
        SsmClient.builder
          .credentialsProvider(awsCredentials)
          .region(region)
          .build()
      )
    )
  }

  implicit val dynamo: Dynamo = {
    val dynamoClient: DynamoDbClient = DynamoDbClient
      .builder()
      .credentialsProvider(awsCredentials)
      .region(region)
      .build()
    new Dynamo(dynamoClient, stage)
  }
  dynamo.initTables()

  val awsAccount = {
    val stsClient: StsClient = StsClient
      .builder()
      .credentialsProvider(awsCredentials)
      .region(region)
      .build()
    val result = stsClient
      .getCallerIdentity(GetCallerIdentityRequest.builder().build())
    val amigoAwsAccount = result.account()
    amigoAwsAccount
  }

  val ec2Client: Ec2Client = Ec2Client
    .builder()
    .region(region)
    .credentialsProvider(awsCredentials)
    .build()

  val amiMetadataLookup: AmiMetadataLookup = new AmiMetadataLookup(ec2Client)

  val prism = new Prism(wsClient)
  val pekkoActorSystem = UntypedActorSystem.create("pekko")

  val prismAgents = new PrismData(
    prism,
    applicationLifecycle,
    pekkoActorSystem.scheduler,
    environment
  )

  // do this synchronously at startup so we can set permissions
  val accountNumbers: Seq[String] =
    Await.result(prism.findAllAWSAccounts(), 30 seconds).map(_.accountNumber)

  val sns: SNS = {
    val snsClient = SnsClient
      .builder()
      .region(region)
      .credentialsProvider(awsCredentials)
      .build()
    new SNS(snsClient, stage, accountNumbers)
  }

  val s3Client: S3Client = S3Client
    .builder()
    .region(region)
    .credentialsProvider(awsCredentials)
    .build()

  val anghammaradSNSClient: SnsAsyncClient =
    SnsAsyncClient
      .builder()
      .region(region)
      .credentialsProvider(awsCredentials)
      .build()

  val amigoUrl: String = configuration
    .get[Option[String]]("amigo.url")
    .getOrElse(s"https://amigo.gutools.co.uk")
  val anghammaradNotificationTopic: Option[String] =
    configuration.get[Option[String]]("anghammarad.sns.topicArn")
  val notificationConfig: Option[NotificationConfig] =
    anghammaradNotificationTopic.map { t =>
      NotificationConfig(amigoUrl, t, anghammaradSNSClient, stage)
    }

  configuration.get[Option[String]]("aws.distributionBucket").foreach {
    bucketName =>
      LambdaDistributionBucket
        .updateBucketPolicy(s3Client, bucketName, stage, accountNumbers)
  }

  val sender: NotificationSender =
    new NotificationSender(sns, region, stage)

  val eventBusActorSystem: ActorSystem[BakeEvent] = {
    val eventListeners = Map(
      "logWriter" -> Behaviours.writeToLog,
      "dynamoWriter" -> Behaviours.persistBakeEvent(notificationConfig),
      "snsWriter" -> Behaviours.sendAmiCreatedNotification(
        sender.sendTopicMessage
      )
    )
    ActorSystem[BakeEvent](Behaviours.guardian(eventListeners), "EventBus")
  }

  implicit val eventBus: ActorSystemWrapper = new ActorSystemWrapper(
    eventBusActorSystem
  )

  implicit val packerConfig: PackerConfig = PackerConfig(
    stage = stage,
    vpcId = configuration.get[Option[String]]("packer.vpcId"),
    subnetId = mandatoryConfig(configuration, "packer.subnetId"),
    instanceProfile =
      configuration.get[Option[String]]("packer.instanceProfile"),
    securityGroupId =
      configuration.get[Option[String]]("packer.securityGroupId")
  )

  val ansibleVariables: Map[String, String] =
    Map(
      "s3_prefix" -> configuration.get[String]("ansible.packages.s3prefix")
    ) ++
      configuration
        .get[Option[String]]("ansible.packages.s3bucket")
        .map("s3_bucket" ->)

  val amigoDataBucket: Option[String] =
    configuration.get[Option[String]]("amigo.data.bucket")

  val packerRunner = new PackerRunner(
    configuration.get[Int]("packer.maxInstances")
  )

  val quartzScheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()

  val scheduledBakesEnabled: Boolean = Try(
    configuration
      .getOptional[Boolean]("amigo.scheduledBakes.enabled")
      .getOrElse(false)
  ).getOrElse(false)

  val scheduledBakeRunner: ScheduledBakeRunner = {
    new ScheduledBakeRunner(
      stage,
      scheduledBakesEnabled,
      prismAgents,
      eventBus,
      ansibleVariables,
      amiMetadataLookup,
      amigoDataBucket,
      packerRunner
    )
  }
  val bakeScheduler = new BakeScheduler(quartzScheduler, scheduledBakeRunner)

  log.info("Registering all scheduled bakes with the scheduler")
  bakeScheduler.initialise(Recipes.list())

  val bakesRepo = new BakesRepo(notificationConfig)
  val packerEC2Client = new PackerEC2Client(ec2Client, stage)

  val bakeDeletionFrequencyMinutes = 1
  val houseKeepingJobs = List(
    new BakeDeletion(
      dynamo,
      awsAccount,
      prismAgents,
      sender,
      bakeDeletionFrequencyMinutes
    ),
    new MarkOldUnusedBakesForDeletion(prismAgents, dynamo),
    new MarkOrphanedBakesForDeletion(prismAgents, dynamo),
    new TimeOutLongRunningBakes(bakesRepo, packerEC2Client),
    new DeleteLongRunningEC2Instances(bakesRepo, packerEC2Client)
  )

  val housekeepingScheduler =
    new HousekeepingScheduler(quartzScheduler, houseKeepingJobs)
  housekeepingScheduler.initialise()

  val debugAvailable = stage != "PROD"

  /** Play 2.8's default is Seq(csrfFilter, securityHeadersFilter,
    * allowedHostsFilter). The allowedHostsFilter is removed here as it causes
    * healthchecks to fail. This service is not accessible on the public
    * internet.
    *
    * We also enable cspFilter, as per
    * https://www.playframework.com/documentation/2.8.x/CspFilter#Enabling-Through-Compile-Time
    */
  override def httpFilters: Seq[EssentialFilter] =
    Seq(csrfFilter, securityHeadersFilter, cspFilter)

  val identityProvider: IdentityProvider =
    IdentityProviderWiring.create(
      environment.mode,
      configuration,
      region,
      wsClient,
      Clock.systemUTC()
    )(executionContext)

  val authAction = new AuthAction[AnyContent](
    identityProvider,
    controllerComponents.parsers.default
  )(executionContext)

  val rootController = new RootController(authAction, controllerComponents)
  val baseImageController =
    new BaseImageController(authAction, prismAgents, controllerComponents)
  val housekeepingController =
    new HousekeepingController(authAction, controllerComponents)
  val roleController = new RoleController(authAction, controllerComponents)
  val recipeController = new RecipeController(
    authAction,
    bakeScheduler,
    prismAgents,
    controllerComponents,
    debugAvailable
  )
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
    bakeDeletionFrequencyMinutes
  )
  lazy val router: Router = new Routes(
    httpErrorHandler,
    rootController,
    baseImageController,
    housekeepingController,
    roleController,
    recipeController,
    bakeController,
    assets
  )
}
