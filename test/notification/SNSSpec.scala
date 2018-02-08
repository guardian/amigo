package notification

import org.scalatest.{ FlatSpec, ShouldMatchers }

class SNSSpec extends FlatSpec with ShouldMatchers {
  "listAwsResource" should "return just the first set if there is no next token" in {
    val results = SNS.listAwsResource {
      case None => List(1, 2, 3, 4) -> None
      case Some(_) => List(5, 6, 7, 8) -> None
    }
    results shouldBe List(1, 2, 3, 4)
  }

  it should "return concatenated sets if next tokens are used" in {
    val results = SNS.listAwsResource {
      case None => List(1, 2, 3, 4) -> Some("next1")
      case Some("next1") => List(5, 6, 7, 8) -> Some("next2")
      case Some("next2") => List(9, 10, 11, 12) -> None
      case Some(_) => throw new IllegalStateException("Uh oh")
    }
    results shouldBe List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
  }
}
