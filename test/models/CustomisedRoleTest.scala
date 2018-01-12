package models

import org.scalatest.{ EitherValues, FunSuite, ShouldMatchers }
import cats.syntax.either._

class CustomisedRoleTest extends FunSuite with ShouldMatchers with EitherValues {

  test("should parse a map of variables") {
    CustomisedRole.formInputTextToVariables("ssh_keys_bucket: bucket, ssh_keys_prefix: Team").right.value
      .shouldBe(Map("ssh_keys_bucket" -> SingleParamValue("bucket"), "ssh_keys_prefix" -> SingleParamValue("Team")))
  }

  test("should parse variable lists") {
    CustomisedRole.formInputTextToVariables("packages: [python-pip, emacs24]").right.value
      .shouldBe(Map("packages" -> ListParamValue.of("python-pip", "emacs24")))
  }

  test("should round trip param values") {
    def roundTrip(pv: ParamValue) = ParamValue.format.read(ParamValue.format.write(pv)).toOption.get
    roundTrip(SingleParamValue("X")) shouldBe SingleParamValue("X")
    roundTrip(DictParamValue(Map("elasticsearch" -> SingleParamValue("http://host.domain:port/flying/monkeys")))) shouldBe DictParamValue(Map("elasticsearch" -> SingleParamValue("http://host.domain:port/flying/monkeys")))
  }

  test("should parse variable dicts") {
    val value = CustomisedRole.formInputTextToVariables("repositories: {elasticsearch: 'http://host.domain:port/flying/monkeys'}")
    value.right.value
      .shouldBe(Map("repositories" -> DictParamValue(Map("elasticsearch" -> SingleParamValue("http://host.domain:port/flying/monkeys")))))
  }
}
