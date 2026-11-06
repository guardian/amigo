package services

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.Results

class TargetHeaderFilterSpec extends AnyFlatSpec with Matchers {
  "targetFromEnvironment" should "identify ECS execution environments" in {
    TargetHeaderFilter.targetFromEnvironment(
      Map("AWS_EXECUTION_ENV" -> "AWS_ECS_EC2")
    ) shouldBe "ecs"
  }

  it should "infer EC2 when no ECS execution environment is present" in {
    TargetHeaderFilter.targetFromEnvironment(Map.empty) shouldBe "ec2"
  }

  "TargetHeaderFilter" should "add the target header without changing redirects or existing headers" in {
    val result = TargetHeaderFilter.addHeader(
      Results.Redirect("/login").withHeaders("X-Existing" -> "preserved"),
      "ecs"
    )

    result.header.status shouldBe 303
    result.header.headers should contain("Location" -> "/login")
    result.header.headers should contain("X-Existing" -> "preserved")
    result.header.headers should contain("X-Gu-Target" -> "ecs")
  }
}
