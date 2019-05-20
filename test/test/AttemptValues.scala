package test

import org.scalatest.EitherValues
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }
import org.scalatest.exceptions.{ StackDepthException, TestFailedException }
import attempt.{ Attempt, Failure }
import org.scalatest.time.{ Millis, Seconds, Span }

import scala.concurrent.ExecutionContext

trait SomePatience extends PatienceConfiguration {
  implicit override val patienceConfig = PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(10, Millis)))
}

trait AttemptValues extends EitherValues with ScalaFutures with SomePatience {
  import StackDepthExceptionHelper.getStackDepthFun
  /**
   * Utility for dealing with attempts in tests
   */
  implicit class RichAttempt[A](attempt: Attempt[A]) {
    def whenReady(implicit ec: ExecutionContext) = attempt.asFuture.futureValue

    def throwableValue: Throwable = {
      val result = try {
        attempt.underlying.futureValue
      } catch {
        case testFailed: TestFailedException => throw testFailed
        case expectedThrowable: Throwable => expectedThrowable
      }
      throw new TestFailedException((_: StackDepthException) => Some(s"Attempt did not result in a thrown exception, got $result instead"), None, getStackDepthFun("AttemptValues.scala", "result"))
    }

    def failureValue: Failure = {
      val underlyingEither: Either[Failure, A] = eitherValue
      try {
        underlyingEither.left.get
      } catch {
        case cause: NoSuchElementException =>
          throw new TestFailedException((_: StackDepthException) => Some(s"Expected Attempt to have failed, but was successful: $underlyingEither"), Some(cause), getStackDepthFun("AttemptValues.scala", "result"))
      }
    }

    def successValue: A = {
      val underlyingEither: Either[Failure, A] = eitherValue
      try {
        underlyingEither.right.get
      } catch {
        case cause: NoSuchElementException =>
          throw new TestFailedException((_: StackDepthException) => Some(s"Expected Attempt to have succeeded, but failed: $underlyingEither"), Some(cause), getStackDepthFun("AttemptValues.scala", "result"))
      }
    }

    def eitherValue = {
      try {
        attempt.underlying.futureValue
      } catch {
        case testFailed: TestFailedException => throw testFailed
        case cause: Throwable =>
          throw new TestFailedException((_: StackDepthException) => Some(s"Attempt resulted in a thrown exception in the underlying Future"), Some(cause), getStackDepthFun("AttemptValues.scala", "result"))
      }
    }
  }
}

object StackDepthExceptionHelper {

  def getStackDepth(stackTrace: Array[StackTraceElement], fileName: String, methodName: String, adjustment: Int = 0) = {
    val stackTraceList = stackTrace.toList

    val fileNameIsDesiredList: List[Boolean] =
      for (element <- stackTraceList) yield element.getFileName == fileName // such as "Checkers.scala"

    val methodNameIsDesiredList: List[Boolean] =
      for (element <- stackTraceList) yield element.getMethodName == methodName // such as "check"

    // For element 0, the previous file name was not desired, because there is no previous
    // one, so you start with false. For element 1, it depends on whether element 0 of the stack trace
    // had the desired file name, and so forth.
    val previousFileNameIsDesiredList: List[Boolean] = false :: (fileNameIsDesiredList.dropRight(1))

    // Zip these two related lists together. They now have two boolean values together, when both
    // are true, that's a stack trace element that should be included in the stack depth.
    val zipped1 = methodNameIsDesiredList zip previousFileNameIsDesiredList
    val methodNameAndPreviousFileNameAreDesiredList: List[Boolean] =
      for ((methodNameIsDesired, previousFileNameIsDesired) <- zipped1) yield methodNameIsDesired && previousFileNameIsDesired

    // Zip the two lists together, that when one or the other is true is an include.
    val zipped2 = fileNameIsDesiredList zip methodNameAndPreviousFileNameAreDesiredList
    val includeInStackDepthList: List[Boolean] =
      for ((fileNameIsDesired, methodNameAndPreviousFileNameAreDesired) <- zipped2) yield fileNameIsDesired || methodNameAndPreviousFileNameAreDesired

    val includeDepth = includeInStackDepthList.takeWhile(include => include).length
    val depth = if (includeDepth == 0 && stackTrace(0).getFileName != fileName && stackTrace(0).getMethodName != methodName)
      stackTraceList.takeWhile(st => st.getFileName != fileName || st.getMethodName != methodName).length
    else
      includeDepth

    depth + adjustment
  }

  def getStackDepthFun(fileName: String, methodName: String, adjustment: Int = 0): (StackDepthException => Int) = { sde =>
    getStackDepth(sde.getStackTrace, fileName, methodName, adjustment)
  }
}