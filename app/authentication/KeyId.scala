package authentication

import java.util.UUID
import scala.util.Try

/** An ALB signing key ID that has been validated as a canonical UUID, so it is
  * safe to use as a path segment in the public-key URL.
  *
  * Construct only via [[KeyId.fromString]].
  */
sealed abstract case class KeyId(value: String)

object KeyId {
  def fromString(candidate: String): Option[KeyId] =
    Option(candidate).filter(isCanonicalUuid).map(new KeyId(_) {})

  // UUID.fromString accepts non-canonical forms such as "1-1-1-1-1".
  private def isCanonicalUuid(candidate: String): Boolean =
    Try(UUID.fromString(candidate)).toOption.exists(
      _.toString.equalsIgnoreCase(candidate)
    )
}
