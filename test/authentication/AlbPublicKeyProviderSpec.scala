package authentication

import authentication.AuthenticationFailure.InvalidPublicKey
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import software.amazon.awssdk.regions.Region

import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.{Base64, UUID}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class AlbPublicKeyProviderSpec
    extends AnyFlatSpec
    with Matchers
    with MockitoSugar {

  private implicit val executionContext: ExecutionContext =
    ExecutionContext.global

  it should "cache a fetched ALB public key by key ID" in {
    val keyId = UUID.randomUUID().toString
    val keyUrl =
      s"https://public-keys.auth.elb.eu-west-1.amazonaws.com/$keyId"
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"))
    val publicKey = keyPairGenerator.generateKeyPair().getPublic

    val wsClient = mock[WSClient]
    val request = mock[WSRequest]
    val response = mock[WSResponse]
    when(wsClient.url(keyUrl)).thenReturn(request)
    when(request.get()).thenReturn(Future.successful(response))
    when(response.status).thenReturn(200)
    when(response.body).thenReturn(pemFor(publicKey))

    val provider =
      new CachingAlbPublicKeyProvider(wsClient, Region.EU_WEST_1)
    val firstResult = Await.result(provider.keyFor(keyId), 2.seconds)
    val secondResult = Await.result(provider.keyFor(keyId), 2.seconds)

    firstResult.isRight should be(true)
    secondResult should be(firstResult)
    verify(wsClient, times(1)).url(keyUrl)
  }

  it should "fetch a different key for a different key ID" in {
    val firstKeyId = UUID.randomUUID().toString
    val secondKeyId = UUID.randomUUID().toString
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(new ECGenParameterSpec("secp256r1"))
    val firstKey = generator.generateKeyPair().getPublic
    val secondKey = generator.generateKeyPair().getPublic
    val wsClient = mock[WSClient]
    val firstRequest = mock[WSRequest]
    val secondRequest = mock[WSRequest]
    val firstResponse = mock[WSResponse]
    val secondResponse = mock[WSResponse]
    when(
      wsClient.url(
        s"https://public-keys.auth.elb.eu-west-1.amazonaws.com/$firstKeyId"
      )
    ).thenReturn(firstRequest)
    when(
      wsClient.url(
        s"https://public-keys.auth.elb.eu-west-1.amazonaws.com/$secondKeyId"
      )
    ).thenReturn(secondRequest)
    when(firstRequest.get()).thenReturn(Future.successful(firstResponse))
    when(secondRequest.get()).thenReturn(Future.successful(secondResponse))
    when(firstResponse.status).thenReturn(200)
    when(secondResponse.status).thenReturn(200)
    when(firstResponse.body).thenReturn(pemFor(firstKey))
    when(secondResponse.body).thenReturn(pemFor(secondKey))

    val provider = new CachingAlbPublicKeyProvider(wsClient, Region.EU_WEST_1)

    Await.result(provider.keyFor(firstKeyId), 2.seconds) shouldBe
      Right(firstKey)
    Await.result(provider.keyFor(secondKeyId), 2.seconds) shouldBe
      Right(secondKey)
    verify(wsClient, times(1)).url(
      s"https://public-keys.auth.elb.eu-west-1.amazonaws.com/$secondKeyId"
    )
  }

  it should "retain only the two most recently fetched keys by default" in {
    val keyIds = Vector.fill(3)(UUID.randomUUID().toString)
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(new ECGenParameterSpec("secp256r1"))
    val wsClient = mock[WSClient]
    val request = mock[WSRequest]
    val response = mock[WSResponse]
    def keyUrl(keyId: String) =
      s"https://public-keys.auth.elb.eu-west-1.amazonaws.com/$keyId"
    keyIds.foreach(keyId =>
      when(wsClient.url(keyUrl(keyId))).thenReturn(request)
    )
    when(request.get()).thenReturn(Future.successful(response))
    when(response.status).thenReturn(200)
    when(response.body).thenReturn(
      pemFor(generator.generateKeyPair().getPublic)
    )

    val provider = new CachingAlbPublicKeyProvider(wsClient, Region.EU_WEST_1)
    keyIds.foreach(keyId => Await.result(provider.keyFor(keyId), 2.seconds))
    Await.result(provider.keyFor(keyIds(1)), 2.seconds)
    Await.result(provider.keyFor(keyIds(0)), 2.seconds)

    verify(wsClient, times(2)).url(keyUrl(keyIds(0)))
    verify(wsClient, times(1)).url(keyUrl(keyIds(1)))
  }

  it should "reject malformed public key PEM" in {
    keyForPem(
      "-----BEGIN PUBLIC KEY-----\ninvalid\n-----END PUBLIC KEY-----"
    ) shouldBe
      Left(InvalidPublicKey)
  }

  it should "reject a non-EC public key" in {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)

    keyForPem(pemFor(generator.generateKeyPair().getPublic)) shouldBe
      Left(InvalidPublicKey)
  }

  private def keyForPem(
      pem: String
  ): Either[AuthenticationFailure, ECPublicKey] = {
    val keyId = UUID.randomUUID().toString
    val wsClient = mock[WSClient]
    val request = mock[WSRequest]
    val response = mock[WSResponse]
    when(
      wsClient.url(
        s"https://public-keys.auth.elb.eu-west-1.amazonaws.com/$keyId"
      )
    ).thenReturn(request)
    when(request.get()).thenReturn(Future.successful(response))
    when(response.status).thenReturn(200)
    when(response.body).thenReturn(pem)

    Await.result(
      new CachingAlbPublicKeyProvider(wsClient, Region.EU_WEST_1)
        .keyFor(keyId),
      2.seconds
    )
  }

  private def pemFor(publicKey: java.security.PublicKey): String = {
    val encodedKey =
      Base64
        .getMimeEncoder(64, "\n".getBytes)
        .encodeToString(
          publicKey.getEncoded
        )
    s"-----BEGIN PUBLIC KEY-----\n$encodedKey\n-----END PUBLIC KEY-----"
  }
}
