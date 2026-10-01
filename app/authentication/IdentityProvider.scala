package authentication

import play.api.mvc.RequestHeader

import scala.concurrent.Future

/** Why a request's identity could not be resolved. `reason` is a stable
  * category for logs and must never contain tokens or personal data.
  */
sealed abstract class AuthenticationFailure(val reason: String)

object AuthenticationFailure {
  case object MissingIdentityHeader
      extends AuthenticationFailure("missing_identity_header")
  case object MalformedToken extends AuthenticationFailure("malformed_token")
  case object InvalidTokenHeader
      extends AuthenticationFailure("invalid_token_header")
  case object InvalidKeyId extends AuthenticationFailure("invalid_key_id")
  case object PublicKeyFetchFailed
      extends AuthenticationFailure("public_key_fetch_failed")
  case object InvalidPublicKey
      extends AuthenticationFailure("invalid_public_key")
  case object SignatureVerificationFailed
      extends AuthenticationFailure("signature_verification_failed")
  case object InvalidSignature
      extends AuthenticationFailure("invalid_signature")
  case object InvalidClaims extends AuthenticationFailure("invalid_claims")
  case object InvalidIdentityClaims
      extends AuthenticationFailure("invalid_identity_claims")
}

trait IdentityProvider {
  def identityFor(
      request: RequestHeader
  ): Future[Either[AuthenticationFailure, UserIdentity]]
}
