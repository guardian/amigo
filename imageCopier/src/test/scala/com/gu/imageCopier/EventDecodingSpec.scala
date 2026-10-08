package com.gu.imageCopier

import io.circe.parser.decode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EventDecodingSpec extends AnyFlatSpec with Matchers {
  "AmiEvent" should "decode the image-copy notification using its explicit decoder" in {
    val json =
      """{"sourceAmi":"ami-123","sourceRegion":"eu-west-1","targetAccounts":["123456789012"],"name":"image","description":"test image","tags":{"Stack":"test"}}"""

    decode[AmiEvent](json) shouldBe Right(
      AmiEvent(
        "ami-123",
        "eu-west-1",
        List("123456789012"),
        "image",
        "test image",
        Map("Stack" -> "test")
      )
    )
  }

  "DeleteEvent" should "decode the housekeeping notification using its explicit decoder" in {
    val json = """{"amis":[{"account":"123456789012","id":"ami-123"}]}"""

    decode[DeleteEvent](json) shouldBe Right(
      DeleteEvent(List(Ami("123456789012", "ami-123")))
    )
  }
}
