package ansible

import models.{ RoleId, Yaml }
import org.scalatest._

class RoleParserSpec extends FlatSpec with Matchers {

  it should "extract dependencies" in {
    val yaml = Yaml(
      """
        |---
        |dependencies:
        |  - foo
        |  - { role: bar, baz: wow }
      """.stripMargin)
    RoleParser.parseDependenciesFromYaml(yaml) should be(Set(RoleId("foo"), RoleId("bar")))
  }
}
