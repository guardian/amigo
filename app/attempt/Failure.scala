package attempt

import java.io.{ PrintWriter, StringWriter }

sealed trait Failure {
  def msg: String
  def cause: Option[Throwable] = None

  def toThrowable: Throwable = cause.getOrElse(new RuntimeException(msg))
}

/**
 * This type of failure has a throwable which could potentially be logged
 */
sealed trait FailureWithThrowable extends Failure {
  def throwable: Throwable
  override def cause = Some(throwable)

  // provide a default mechanism for showing the exception to a user
  override def msg = {
    val stringWriter = new StringWriter()
    val writer = new PrintWriter(stringWriter)
    throwable.printStackTrace(writer)

    stringWriter.toString
  }
}

case object PrismNotInitialisedFailure extends Failure {
  val msg = "Prism is not yet initialised"
}

case class PrismStaleFailure(msg: String) extends Failure

case class JsonParseFailure(msg: String) extends Failure

case class HousekeepingFailure(msg: String) extends Failure

case class ConfigurationFailure(msg: String) extends Failure

case class UnknownFailure(throwable: Throwable) extends FailureWithThrowable

case class AwsSdkFailure(throwable: Throwable) extends FailureWithThrowable

object Failure {
  def collect[A](eithers: List[Either[Failure, A]])(recurse: A => List[Failure]): List[Failure] = {
    eithers.flatMap {
      case Left(failure) => List(failure)
      case Right(success) => recurse(success)
    }
  }
}