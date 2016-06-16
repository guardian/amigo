import cats.data.Xor
import play.api.data.Form

package object controllers {
  implicit class FormAsXor[T](form: Form[T]) {
    def toXor = form.fold(Xor.left, Xor.right)
  }
}
