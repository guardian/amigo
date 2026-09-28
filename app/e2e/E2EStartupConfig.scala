package e2e

sealed trait E2EScenario {
  def name: String
}

object E2EScenario {
  case object Empty extends E2EScenario {
    override val name: String = "empty"
  }

  case object Populated extends E2EScenario {
    override val name: String = "populated"
  }

  val all: Seq[E2EScenario] = Seq(Empty, Populated)
}

final case class E2EStartupConfig(scenario: E2EScenario)

object E2EStartupConfig {
  val EnabledVariable: String = "AMIGO_E2E_ENABLED"
  val ScenarioVariable: String = "AMIGO_E2E_SCENARIO"

  def fromEnvironment(
      environment: Map[String, String]
  ): Either[String, E2EStartupConfig] =
    for {
      _ <- Either.cond(
        environment.get(EnabledVariable).contains("true"),
        (),
        s"$EnabledVariable must be set to true"
      )
      scenarioName <- environment
        .get(ScenarioVariable)
        .filter(_.nonEmpty)
        .toRight(s"$ScenarioVariable must be set")
      scenario <- E2EScenario.all
        .find(_.name == scenarioName)
        .toRight(
          s"Unknown $ScenarioVariable '$scenarioName'; expected one of: ${E2EScenario.all.map(_.name).mkString(", ")}"
        )
    } yield E2EStartupConfig(scenario)
}
