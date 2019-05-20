import cats.syntax.either._

package object attempt {

  implicit class RichOption[A](option: Option[A]) {
    def toAttempt(whenNone: => Failure): Attempt[A] = Attempt.fromOption(option, whenNone)
  }

  implicit class RichFailureEither[A](either: Either[Failure, A]) {
    def toAttempt = Attempt.fromEither(either)
  }

  implicit class RichEither[Left, A](either: Either[Left, A]) {
    def toAttempt(leftToFailure: Left => Failure) = Attempt.fromEither(either.leftMap(leftToFailure))
  }
}
