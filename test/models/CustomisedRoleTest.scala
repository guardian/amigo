package models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.syntax.either._
import org.scalatest.EitherValues

class CustomisedRoleTest extends AnyFlatSpec with Matchers with EitherValues {

  "CustomisedRole.formInputTextToVariables" should "parse a map of variables" in {
    CustomisedRole.formInputTextToVariables("ssh_keys_bucket: bucket, ssh_keys_prefix: Team").value
      .shouldBe(Map("ssh_keys_bucket" -> SingleParamValue("bucket"), "ssh_keys_prefix" -> SingleParamValue("Team")))
  }

  "CustomisedRole.formInputTextToVariables" should "parse variable lists" in {
    CustomisedRole.formInputTextToVariables("packages: [python-pip, emacs24]").value
      .shouldBe(Map("packages" -> ListParamValue.of("python-pip", "emacs24")))
  }

  "ParamValue.format" should "round trip param values" in {
    def roundTrip(pv: ParamValue) = ParamValue.format.read(ParamValue.format.write(pv)).toOption.get
    roundTrip(SingleParamValue("X")) shouldBe SingleParamValue("X")
    roundTrip(DictParamValue(Map("elasticsearch" -> SingleParamValue("http://host.domain:port/flying/monkeys")))) shouldBe DictParamValue(Map("elasticsearch" -> SingleParamValue("http://host.domain:port/flying/monkeys")))
  }

  "CustomisedRole.formInputTextToVariables" should "parse variable dicts" in {
    val result = CustomisedRole.formInputTextToVariables("repositories: {elasticsearch: 'http://host.domain:port/flying/monkeys'}")
    result.value
      .shouldBe(Map("repositories" -> DictParamValue(Map("elasticsearch" -> SingleParamValue("http://host.domain:port/flying/monkeys")))))
  }
}
