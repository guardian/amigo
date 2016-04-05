package components

import akka.typed._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ InstanceProfileCredentialsProvider, EnvironmentVariableCredentialsProvider, AWSCredentialsProviderChain }
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.gu.cm.{ ConfigurationLoader, Identity }
import com.gu.googleauth.GoogleAuthConfig
import controllers.{ Auth, Amigo }
import data.Dynamo
import event.{ ActorSystemWrapper, Behaviours, BakeEvent }
import org.joda.time.Duration
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.i18n.I18nComponents
import play.api.libs.iteratee.Concurrent
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import router.Routes

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with I18nComponents {

  val identity = {
    import com.gu.cm.PlayImplicits._
    Identity.whoAmI("amigo", context.environment.mode)
  }
  override lazy val configuration = context.initialConfiguration ++ ConfigurationLoader.playConfig(identity, context.environment.mode)

  def mandatoryConfig(key: String): String = configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  implicit val dynamo = {
    val awsCreds = new AWSCredentialsProviderChain(
      new EnvironmentVariableCredentialsProvider,
      new ProfileCredentialsProvider("deployTools"),
      new InstanceProfileCredentialsProvider
    )
    val region = Regions.fromName(configuration.getString("aws.region").getOrElse("eu-west-1"))
    val dynamoClient: AmazonDynamoDBClient = new AmazonDynamoDBClient(awsCreds).withRegion(region)
    new Dynamo(dynamoClient, identity.stage)
  }
  dynamo.initTables()

  val (eventsOut, eventsChannel) = Concurrent.broadcast[BakeEvent]
  val eventBusActorSystem = {
    val eventListeners = Map(
      "channelSender" -> Props(Behaviours.sendToChannel(eventsChannel)),
      "logWriter" -> Props(Behaviours.writeToLog),
      "dynamoWriter" -> Props(Behaviours.writeToDynamo)
    )
    ActorSystem[BakeEvent]("EventBus", Props(Behaviours.guardian(eventListeners)))
  }
  val eventBus = new ActorSystemWrapper(eventBusActorSystem)

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = Some("guardian.co.uk"),
    maxAuthAge = Some(Duration.standardDays(90)),
    enforceValidity = true
  )

  val controller = new Amigo(eventsOut, eventBus, googleAuthConfig, messagesApi)
  val authController = new Auth(googleAuthConfig)(wsClient)
  val assets = new controllers.Assets(httpErrorHandler)
  lazy val router: Router = new Routes(httpErrorHandler, controller, authController, assets)
}
