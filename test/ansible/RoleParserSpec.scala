package ansible

import models.{ RoleId, Yaml }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RoleParserSpec extends AnyFlatSpec with Matchers {

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
