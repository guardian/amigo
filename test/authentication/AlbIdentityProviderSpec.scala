package authentication

import authentication.AuthenticationFailure._
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.slf4j.LoggerFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.FakeRequest

import java.security.KeyPairGenerator
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.security.spec.ECGenParameterSpec
import java.time.{Clock, Instant, ZoneOffset}
import java.util.{Date, UUID}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class AlbIdentityProviderSpec extends AnyFlatSpec with Matchers {

  private implicit val executionContext: ExecutionContext =
    ExecutionContext.global
  private val now = Instant.parse("2026-09-29T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val keyId = UUID.randomUUID().toString
  private val signerArn =
    "arn:aws:elasticloadbalancing:eu-west-1:123456789012:loadbalancer/app/amigo/example"

  private val keyPair = {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(new ECGenParameterSpec("secp256r1"))
    generator.generateKeyPair()
  }
  private val publicKey = keyPair.getPublic.asInstanceOf[ECPublicKey]
  private val privateKey = keyPair.getPrivate.asInstanceOf[ECPrivateKey]

  private val publicKeyProvider = new AlbPublicKeyProvider {
    override def keyFor(
        requestedKeyId: KeyId
    ): Future[Either[AuthenticationFailure, ECPublicKey]] =
      Future.successful(Right(publicKey))
  }

  private val provider = new AlbIdentityProvider(
    AlbIdentityConfig(signerArn),
    publicKeyProvider,
    clock
  )

  it should "translate a valid ALB identity" in {
    val result = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "true",
          "name" -> "Example User"
        )
      )
    )

    result should be(Right(UserIdentity("user@example.com", "Example User")))
  }

  it should "reject a missing name" in {
    val result = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "true"
        )
      )
    )

    result shouldBe Left(InvalidIdentityClaims)
  }

  it should "reject a non-string name" in {
    val result = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "true",
          "name" -> 123
        )
      )
    )

    result shouldBe Left(InvalidClaims)
  }

  it should "reject an unverified email" in {
    val result = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "false",
          "name" -> "Example User"
        )
      )
    )

    result shouldBe Left(InvalidIdentityClaims)
  }

  it should "reject a token issued for another load balancer" in {
    val result = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "true",
          "name" -> "Example User"
        ),
        headerOverrides = Map("signer" -> "another-load-balancer")
      )
    )

    result shouldBe Left(InvalidTokenHeader)
  }

  it should "reject an expired token outside the allowed clock skew" in {
    val result = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "true",
          "name" -> "Example User"
        ),
        expiresAt = now.minusSeconds(61)
      )
    )

    result shouldBe Left(InvalidTokenHeader)
  }

  it should "accept a token at the allowed clock skew boundary" in {
    val result = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "true",
          "name" -> "Example User"
        ),
        expiresAt = now.minusSeconds(60)
      )
    )

    result should be(
      Right(UserIdentity("user@example.com", "Example User"))
    )
  }

  it should "reject false, missing, and malformed verified-email claims" in {
    Seq[(Any, AuthenticationFailure)](
      "false" -> InvalidIdentityClaims,
      "TRUE" -> InvalidClaims,
      " true " -> InvalidClaims,
      true -> InvalidClaims,
      false -> InvalidClaims,
      1 -> InvalidClaims
    ).foreach { case (claim, failure) =>
      val result = identityFor(
        token(
          Map(
            "sub" -> "subject",
            "email" -> "user@example.com",
            "email_verified" -> claim,
            "name" -> "Example User"
          )
        )
      )
      result shouldBe Left(failure)
    }

    val missing = identityFor(
      token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "name" -> "Example User"
        )
      )
    )
    missing shouldBe Left(InvalidIdentityClaims)
  }

  it should "reject a request without the ALB identity header" in {
    val result = Await.result(
      provider.identityFor(FakeRequest()),
      2.seconds
    )

    result shouldBe Left(MissingIdentityHeader)
  }

  it should "log only rejection reasons without personal data" in {
    val logger = LoggerFactory
      .getLogger(classOf[AlbIdentityProvider])
      .asInstanceOf[Logger]
    val previousLevel = logger.getLevel
    val appender = new ListAppender[ILoggingEvent]()
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.INFO)

    try {
      val namedToken = token(
        Map(
          "sub" -> "subject",
          "email" -> "user@example.com",
          "email_verified" -> "true",
          "name" -> "Example User"
        )
      )
      identityFor(namedToken)
      identityFor("not-a-jwt")
      Await.result(provider.identityFor(FakeRequest()), 2.seconds)

      val messages = appender.list.asScala.map(_.getFormattedMessage).toList
      messages shouldBe List(
        "ALB authentication failed: malformed_token",
        "ALB authentication failed: missing_identity_header"
      )
      val logged = messages.mkString("\n")
      logged should not include "user@example.com"
      logged should not include "Example User"
      logged should not include namedToken
    } finally {
      logger.detachAppender(appender)
      logger.setLevel(previousLevel)
      appender.stop()
    }
  }

  private def identityFor(
      encodedToken: String
  ): Either[AuthenticationFailure, UserIdentity] =
    Await.result(
      provider.identityFor(
        FakeRequest().withHeaders(
          AlbIdentityProvider.IdentityHeader -> encodedToken
        )
      ),
      2.seconds
    )

  private def token(
      claims: Map[String, Any],
      headerOverrides: Map[String, Any] = Map.empty,
      expiresAt: Instant = now.plusSeconds(300)
  ): String = {
    val headerValues = Map[String, Any](
      "signer" -> signerArn,
      "exp" -> expiresAt.getEpochSecond
    ) ++ headerOverrides
    val headerBuilder = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId)
    headerValues.foreach { case (name, value) =>
      headerBuilder.customParam(name, value)
    }

    val claimsBuilder = new JWTClaimsSet.Builder()
    claims.foreach { case (name, value) => claimsBuilder.claim(name, value) }
    claimsBuilder.issueTime(Date.from(now))

    val signedToken =
      new SignedJWT(headerBuilder.build(), claimsBuilder.build())
    signedToken.sign(new ECDSASigner(privateKey))
    signedToken.serialize()
  }
}
