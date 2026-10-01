package authentication

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class KeyIdSpec extends AnyFlatSpec with Matchers {

  it should "accept a canonical UUID in either case" in {
    val uuid = UUID.randomUUID().toString

    KeyId.fromString(uuid).map(_.value) shouldBe Some(uuid)
    KeyId.fromString(uuid.toUpperCase).map(_.value) shouldBe
      Some(uuid.toUpperCase)
  }

  it should "reject values that are not canonical UUIDs" in {
    Seq(
      null,
      "",
      "not-a-uuid",
      "1-1-1-1-1",
      s"../${UUID.randomUUID()}",
      s"${UUID.randomUUID()}/extra"
    ).foreach(candidate => KeyId.fromString(candidate) shouldBe None)
  }
}
