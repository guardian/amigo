package authentication

import authentication.AuthenticationFailure._
import com.nimbusds.jose.jwk.{ECKey, JWK}
import play.api.http.Status.OK
import play.api.libs.ws.WSClient
import software.amazon.awssdk.regions.Region

import java.security.interfaces.ECPublicKey
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait AlbPublicKeyProvider {
  def keyFor(
      keyId: String
  ): Future[Either[AuthenticationFailure, ECPublicKey]]
}

/** Fetches ALB JWT signing keys by key ID and caches recent keys so tokens
  * signed before a key rotation can still be verified without repeated fetches.
  */
final class CachingAlbPublicKeyProvider(
    wsClient: WSClient,
    region: Region
)(implicit executionContext: ExecutionContext)
    extends AlbPublicKeyProvider {

  private val maximumEntries = 2
  private val cachedKeys =
    new AtomicReference(Vector.empty[(String, ECPublicKey)])

  override def keyFor(
      keyId: String
  ): Future[Either[AuthenticationFailure, ECPublicKey]] =
    cachedKeys.get().collectFirst {
      case (cachedKeyId, publicKey) if cachedKeyId == keyId =>
        publicKey
    } match {
      case Some(publicKey) => Future.successful(Right(publicKey))
      case None            => fetchAndCache(keyId)
    }

  private def fetchAndCache(
      keyId: String
  ): Future[Either[AuthenticationFailure, ECPublicKey]] = {
    val keyUrl =
      s"https://public-keys.auth.elb.${region.id}.amazonaws.com/$keyId"
    wsClient
      .url(keyUrl)
      .get()
      .map { response =>
        if (response.status == OK) {
          parsePublicKey(response.body).toEither.left
            .map(_ => InvalidPublicKey)
            .map { publicKey =>
              cache(keyId, publicKey)
              publicKey
            }
        } else {
          Left(PublicKeyFetchFailed)
        }
      }
      .recover { case _ => Left(PublicKeyFetchFailed) }
  }

  private def parsePublicKey(pem: String): Try[ECPublicKey] = Try {
    JWK.parseFromPEMEncodedObjects(pem) match {
      case key: ECKey => key.toECPublicKey
      case _ => throw new IllegalArgumentException("Expected EC public key")
    }
  }

  @tailrec
  private def cache(keyId: String, publicKey: ECPublicKey): Unit = {
    // Replace the key as the newest entry and evict the oldest if full.
    // Retry if another thread changed the cache before the update.
    val existing = cachedKeys.get()
    val updated =
      (existing.filterNot { case (cachedKeyId, _) => cachedKeyId == keyId } :+
        (keyId -> publicKey)).takeRight(maximumEntries)
    if (!cachedKeys.compareAndSet(existing, updated)) {
      cache(keyId, publicKey)
    }
  }
}
