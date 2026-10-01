package authentication

import authentication.AuthenticationFailure._
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import play.api.mvc.RequestHeader
import services.Loggable

import java.security.interfaces.ECPublicKey
import java.time.{Clock, Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Resolves a request identity from the ALB-signed `x-amzn-oidc-data` header.
  *
  * Requires ES256, the configured signer ARN, an unexpired header `exp` within
  * 60 seconds of clock skew, and a UUID key ID. The signature is verified with
  * the ALB public key before reading identity claims.
  *
  * Builds [[UserIdentity]] from the `email` and `name` string claims only when
  * Cognito's `email_verified` claim is exactly the string `"true"`. Claim
  * values are used as supplied, without trimming or name fallbacks. Missing
  * headers, invalid assertions and public-key lookup failures are rejected;
  * rejection reasons are logged without recording the token or personal data.
  *
  * Google group authorisation is enforced upstream by Cognito Gatekeeper.
  *
  * @param expectedSignerArn
  *   ARN of the ALB that must have signed the assertion.
  * @param publicKeyProvider
  *   Supplies the ALB signing public key for the validated key ID.
  * @param clock
  *   Time source used to validate the assertion's header expiration.
  */
final class AlbIdentityProvider(
    expectedSignerArn: String,
    publicKeyProvider: AlbPublicKeyProvider,
    clock: Clock
)(implicit executionContext: ExecutionContext)
    extends IdentityProvider
    with Loggable {

  import AlbIdentityProvider._

  override def identityFor(
      request: RequestHeader
  ): Future[Either[AuthenticationFailure, UserIdentity]] = {
    val result = request.headers.get(IdentityHeader) match {
      case None =>
        Future.successful(Left(MissingIdentityHeader))
      case Some(encodedToken) =>
        parseToken(encodedToken) match {
          case Left(failure) => Future.successful(Left(failure))
          case Right(token)  =>
            validateHeader(token) match {
              case Left(failure) => Future.successful(Left(failure))
              case Right(keyId)  =>
                publicKeyProvider
                  .keyFor(keyId)
                  .map(_.flatMap(validateToken(token, _)))
            }
        }
    }
    result.map { outcome =>
      outcome.left.foreach(failure =>
        log.warn("ALB authentication failed: {}", failure.reason)
      )
      outcome
    }
  }

  private def parseToken(
      encodedToken: String
  ): Either[AuthenticationFailure, SignedJWT] =
    Try(SignedJWT.parse(encodedToken)).toEither.left
      .map(_ => MalformedToken)

  private def validateHeader(
      token: SignedJWT
  ): Either[AuthenticationFailure, KeyId] = {
    val header = token.getHeader
    val expiration = Option(header.getCustomParam("exp")).collect {
      case number: Number => Instant.ofEpochSecond(number.longValue())
    }
    val validKeyId = Option(header.getKeyID).flatMap(KeyId.fromString)
    val valid =
      header.getAlgorithm == JWSAlgorithm.ES256 &&
        header.getCustomParam("signer") == expectedSignerArn &&
        expiration.exists(
          !_.plus(AllowedClockSkew).isBefore(clock.instant())
        )

    if (valid) {
      validKeyId.toRight(InvalidKeyId)
    } else {
      Left(InvalidTokenHeader)
    }
  }

  private def validateToken(
      token: SignedJWT,
      publicKey: ECPublicKey
  ): Either[AuthenticationFailure, UserIdentity] =
    Try(token.verify(new ECDSAVerifier(publicKey))).toEither.left
      .map(_ => SignatureVerificationFailed)
      .flatMap {
        case false => Left(InvalidSignature)
        case true  => identityFromClaims(token)
      }

  private def identityFromClaims(
      token: SignedJWT
  ): Either[AuthenticationFailure, UserIdentity] =
    Try {
      val claims = token.getJWTClaimsSet
      val subject = Option(claims.getSubject)
      val name = Option(claims.getStringClaim("name"))
      val email = Option(claims.getStringClaim("email"))
      // Cognito's userInfo endpoint serialises email_verified as a string.
      val emailVerified = claims.getStringClaim("email_verified") match {
        case "true"         => true
        case "false" | null => false
        case _              =>
          throw new IllegalArgumentException("Invalid email_verified claim")
      }

      for {
        fullName <- name
        validEmail <- email
        if emailVerified
      } yield UserIdentity(validEmail, fullName)
    }.toEither.left
      .map(_ => InvalidClaims)
      .flatMap(_.toRight(InvalidIdentityClaims))
}

object AlbIdentityProvider {
  val IdentityHeader = "x-amzn-oidc-data"

  private val AllowedClockSkew = Duration.ofSeconds(60)
}
