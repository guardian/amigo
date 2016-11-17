package models

import org.scalatest.{ FunSuite, ShouldMatchers }

class CustomisedRoleTest extends FunSuite with ShouldMatchers {

  test("should parse a map of variables") {
    CustomisedRole.formInputTextToVariables("ssh_keys_bucket: bucket, ssh_keys_prefix: Team")
      .shouldBe(Map("ssh_keys_bucket" -> SingleParamValue("bucket"), "ssh_keys_prefix" -> SingleParamValue("Team")))
  }

  test("should parse variable lists") {
    CustomisedRole.formInputTextToVariables("packages: [python-pip, emacs24]")
      .shouldBe(Map("packages" -> ListParamValue.of("python-pip", "emacs24")))
  }

  test("should round trip param values") {
    def roundTrip(pv: ParamValue) = ParamValue.format.read(ParamValue.format.write(pv)).toOption.get
    roundTrip(SingleParamValue("X")) shouldBe (SingleParamValue("X"))
  }
}
