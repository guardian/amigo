package e2e

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class E2EStartupConfigSpec extends AnyFlatSpec with Matchers {

  it should "reject startup without the explicit E2E marker" in {
    E2EStartupConfig.fromEnvironment(
      Map(E2EStartupConfig.ScenarioVariable -> "empty")
    ) should be(Left("AMIGO_E2E_ENABLED must be set to true"))
  }

  it should "reject startup without a fixture scenario" in {
    E2EStartupConfig.fromEnvironment(
      Map(E2EStartupConfig.EnabledVariable -> "true")
    ) should be(Left("AMIGO_E2E_SCENARIO must be set"))
  }

  it should "reject an unknown fixture scenario" in {
    E2EStartupConfig.fromEnvironment(
      Map(
        E2EStartupConfig.EnabledVariable -> "true",
        E2EStartupConfig.ScenarioVariable -> "production"
      )
    ) should be(
      Left(
        "Unknown AMIGO_E2E_SCENARIO 'production'; expected one of: empty, populated"
      )
    )
  }

  it should "accept the empty fixture scenario" in {
    E2EStartupConfig.fromEnvironment(
      Map(
        E2EStartupConfig.EnabledVariable -> "true",
        E2EStartupConfig.ScenarioVariable -> "empty"
      )
    ) should be(Right(E2EStartupConfig(E2EScenario.Empty)))
  }

  it should "accept the populated fixture scenario" in {
    E2EStartupConfig.fromEnvironment(
      Map(
        E2EStartupConfig.EnabledVariable -> "true",
        E2EStartupConfig.ScenarioVariable -> "populated"
      )
    ) should be(Right(E2EStartupConfig(E2EScenario.Populated)))
  }
}
