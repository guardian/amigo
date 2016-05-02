package components

import akka.stream.scaladsl.Source
import akka.typed._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ InstanceProfileCredentialsProvider, AWSCredentialsProviderChain }
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{ AmazonDynamoDB, AmazonDynamoDBClient }
import com.gu.cm.{ ConfigurationLoader, Identity }
import com.gu.googleauth.GoogleAuthConfig
import data._
import org.joda.time.Duration
import play.api.ApplicationLoader.Context
import play.api.libs.streams.Streams
import play.api.{ BuiltInComponentsFromContext, Configuration }
import play.api.i18n.I18nComponents
import play.api.libs.iteratee.Concurrent
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router

import data.Dynamo
import prism.Prism
import packer.PackerConfig
import event.{ ActorSystemWrapper, Behaviours, BakeEvent }
import controllers._

import router.Routes

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with I18nComponents {

  val identity = {
    import com.gu.cm.PlayImplicits._
    Identity.whoAmI("amigo", context.environment.mode)
  }
  override lazy val configuration: Configuration = context.initialConfiguration ++ ConfigurationLoader.playConfig(identity, context.environment.mode)

  def mandatoryConfig(key: String): String = configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  implicit val dynamo = {
    val awsCreds = new AWSCredentialsProviderChain(
      new ProfileCredentialsProvider("deployTools"),
      new ProfileCredentialsProvider(),
      new InstanceProfileCredentialsProvider
    )
    val region = configuration.getString("aws.region").map(Regions.fromName).getOrElse(Regions.EU_WEST_1)
    val dynamoClient: AmazonDynamoDB = new AmazonDynamoDBClient(awsCreds).withRegion(region)
    new Dynamo(dynamoClient, identity)
  }
  dynamo.initTables()

  val baseImages = new BaseImages(identity)
  val recipes = new Recipes(identity, baseImages)
  val bakes = new Bakes(identity, recipes)
  val bakeLogs = new BakeLogs(identity)

  val (eventsEnumerator, eventsChannel) = Concurrent.broadcast[BakeEvent]
  val eventsSource = Source.fromPublisher(Streams.enumeratorToPublisher(eventsEnumerator))
  val behaviours = new Behaviours(bakes, bakeLogs)
  val eventBusActorSystem = {
    val eventListeners = Map(
      "channelSender" -> Props(behaviours.sendToChannel(eventsChannel)),
      "logWriter" -> Props(behaviours.writeToLog),
      "dynamoWriter" -> Props(behaviours.writeToDynamo)
    )
    ActorSystem[BakeEvent]("EventBus", Props(behaviours.guardian(eventListeners)))
  }
  implicit val eventBus = new ActorSystemWrapper(eventBusActorSystem)

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = Some("guardian.co.uk"),
    maxAuthAge = Some(Duration.standardDays(90)),
    enforceValidity = true
  )

  implicit val packerConfig = PackerConfig(
    vpcId = configuration.getString("packer.vpcId"),
    subnetId = configuration.getString("packer.subnetId")
  )

  val prism = new Prism(wsClient)

  val rootController = new RootController(googleAuthConfig)
  val baseImageController = new BaseImageController(googleAuthConfig, messagesApi, baseImages)
  val roleController = new RoleController(googleAuthConfig)
  val recipeController = new RecipeController(googleAuthConfig, messagesApi, recipes, bakes, baseImages)
  val bakeController = new BakeController(eventsSource, prism, googleAuthConfig, messagesApi, recipes, bakes, bakeLogs)
  val authController = new Auth(googleAuthConfig)(wsClient)
  val assets = new controllers.Assets(httpErrorHandler)
  lazy val router: Router = new Routes(
    httpErrorHandler,
    rootController,
    baseImageController,
    roleController,
    recipeController,
    bakeController,
    authController,
    assets)
}
