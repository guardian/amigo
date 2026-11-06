package services

import play.api.mvc.{EssentialAction, EssentialFilter, Result}

import scala.concurrent.ExecutionContext

class TargetHeaderFilter(target: String)(implicit
    executionContext: ExecutionContext
) extends EssentialFilter {
  override def apply(next: EssentialAction): EssentialAction =
    EssentialAction { request =>
      next(request).map(TargetHeaderFilter.addHeader(_, target))
    }
}

object TargetHeaderFilter {
  val HeaderName = "X-Gu-Target"

  def addHeader(result: Result, target: String): Result =
    result.withHeaders(HeaderName -> target)

  def targetFromEnvironment(environment: Map[String, String]): String =
    if (
      environment
        .get("AWS_EXECUTION_ENV")
        .exists(_.startsWith("AWS_ECS_"))
    ) "ecs"
    else "ec2"
}
