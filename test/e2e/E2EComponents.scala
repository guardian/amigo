package e2e

import authentication.AuthAction
import components.IdentityProviderWiring
import controllers._
import data.Dynamo
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, Mode}
import play.api.i18n.I18nComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import play.filters.csp.CSPComponents
import router.Routes
import schedule.BakeScheduler
import services.PrismData
import software.amazon.awssdk.regions.Region

import java.time.Clock

private[e2e] final class E2EComponents(context: Context, prismBaseUrl: String)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with I18nComponents
    with AssetsComponents
    with HttpFiltersComponents
    with CSPComponents {

  require(
    environment.mode == Mode.Dev,
    "E2E bootstrap requires development mode"
  )

  private implicit val dynamo: Dynamo = E2EFixtures.dynamo()
  private val prismData = new PrismData(
    new prism.Prism(wsClient, prismBaseUrl),
    applicationLifecycle,
    actorSystem.scheduler,
    environment
  )(executionContext)
  private val identityProvider = IdentityProviderWiring.create(
    environment.mode,
    configuration,
    Region.EU_WEST_1,
    wsClient,
    Clock.systemUTC()
  )(executionContext)
  private val authAction = new AuthAction[AnyContent](
    identityProvider,
    controllerComponents.parsers.default
  )(executionContext)

  override def httpFilters: Seq[EssentialFilter] =
    Seq(csrfFilter, securityHeadersFilter, cspFilter)

  override lazy val router: Router = new Routes(
    httpErrorHandler,
    new RootController(authAction, controllerComponents),
    new BaseImageController(authAction, prismData, controllerComponents),
    new HousekeepingController(authAction, controllerComponents),
    new RoleController(authAction, controllerComponents),
    new RecipeController(
      authAction,
      E2EFixtures.disabled(classOf[BakeScheduler]),
      prismData,
      controllerComponents,
      debugAvailable = false
    ),
    E2EFixtures.disabled(classOf[BakeController]),
    assets
  )
}
