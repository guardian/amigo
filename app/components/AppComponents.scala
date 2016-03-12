package components

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ InstanceProfileCredentialsProvider, EnvironmentVariableCredentialsProvider, AWSCredentialsProviderChain }
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import controllers.Amigo
import data.Dynamo
import event.{ BakeEvent, ChannelWrapper }
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.iteratee.Concurrent
import play.api.routing.Router
import router.Routes

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context) {

  implicit val dynamo = {
    val awsCreds = new AWSCredentialsProviderChain(
      new EnvironmentVariableCredentialsProvider,
      new ProfileCredentialsProvider("deployTools"),
      new InstanceProfileCredentialsProvider
    )
    val region = Regions.fromName(configuration.getString("aws.region").getOrElse("eu-west-1"))
    val dynamoClient: AmazonDynamoDBClient = new AmazonDynamoDBClient(awsCreds).withRegion(region)
    val stage = AWS.discoverStage()
    new Dynamo(dynamoClient, stage)
  }
  dynamo.initTables()

  val (eventsOut, eventsChannel) = Concurrent.broadcast[BakeEvent]
  val eventBus = new ChannelWrapper(eventsChannel)
  val controller = new Amigo(eventsOut, eventBus)
  val assets = new controllers.Assets(httpErrorHandler)
  lazy val router: Router = new Routes(httpErrorHandler, controller, assets)
}

object AWS {

  def discoverStage(): String = {
    // TODO lookup Stage tag. configuration-magic has helpers to do this.
    "DEV"
  }

}