package e2e

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.{ApplicationLoader, Environment, Mode}

class E2EComponentsSpec extends AnyFlatSpec with Matchers {
  it should "reject the reloading bootstrap outside development mode" in {
    for (mode <- Seq(Mode.Prod, Mode.Test)) {
      val context =
        ApplicationLoader.Context.create(Environment.simple(mode = mode))
      val error = intercept[IllegalArgumentException] {
        new E2EApplicationLoader().load(context)
      }
      error.getMessage shouldBe
        "requirement failed: E2E bootstrap requires development mode"
    }
  }

  it should "reject the offline bootstrap outside development mode" in {
    for (mode <- Seq(Mode.Prod, Mode.Test)) {
      val context =
        ApplicationLoader.Context.create(Environment.simple(mode = mode))
      val error = intercept[IllegalArgumentException] {
        new E2EComponents(context, "http://127.0.0.1:1")
      }
      error.getMessage shouldBe
        "requirement failed: E2E bootstrap requires development mode"
    }
  }
}
