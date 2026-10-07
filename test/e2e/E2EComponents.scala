package e2e

import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth.{
  AntiForgeryChecker,
  AuthAction,
  GoogleAuthConfig,
  GoogleGroupChecker
}
import com.gu.play.secretrotation.DualSecretTransition.InitialSecret
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

import java.io.FileInputStream
import java.time.Duration
import scala.util.Using

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
  private val authConfig = GoogleAuthConfig(
    clientId = "e2e-client",
    clientSecret = "e2e-client-not-a-real-secret",
    redirectUrl = configuration.get[String]("google.redirectUrl"),
    domains = List("guardian.co.uk"),
    maxAuthAge = Some(Duration.ofDays(90)),
    enforceValidity = true,
    antiForgeryChecker = AntiForgeryChecker(
      InitialSecret(configuration.get[String]("play.http.secret.key"))
    )
  )
  private val serviceAccount = Using.resource(
    new FileInputStream(configuration.get[String]("e2e.serviceAccountPath"))
  )(ServiceAccountCredentials.fromStream)
  private val groupChecker = new GoogleGroupChecker(
    "e2e-admin@guardian.co.uk",
    serviceAccount
  )
  private val authAction = new AuthAction[AnyContent](
    authConfig,
    controllers.routes.Login.loginAction(),
    controllerComponents.parsers.default
  )(executionContext)
  private val login = new Login(
    authConfig,
    wsClient,
    controllerComponents,
    Set(
      "e2e-department@guardian.co.uk",
      "e2e-data@guardian.co.uk",
      "e2e-multimedia@guardian.co.uk"
    ),
    groupChecker
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
    login,
    assets
  )
}
