package data

import com.gu.googleauth.UserIdentity
import com.gu.scanamo.ops._
import controllers.AuthActions
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc.{ Action, AnyContent, BodyParser, Result }

trait OpActions extends AuthActions {
  def dynamo: Dynamo

  object AuthOpAction {
    def apply(action: AuthenticatedRequest[AnyContent, UserIdentity] => ScanamoOps[Result]): Action[AnyContent] = {
      AuthAction.apply(action.andThen(dynamo.exec))
    }

    def apply(action: => ScanamoOps[Result]): Action[AnyContent] = {
      AuthAction.apply(dynamo.exec(action))
    }

    def apply[A](bodyParser: BodyParser[A])(action: AuthenticatedRequest[A, UserIdentity] => ScanamoOps[Result]): Action[A] = {
      AuthAction.apply(bodyParser)(action.andThen(dynamo.exec))
    }
  }
}
