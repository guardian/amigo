package models

import fastparse.Parsed
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class CustomisedRoleTest extends AnyFlatSpec with Matchers with EitherValues {

  "CustomisedRole.key" should "parse a key" in {
    val sourceString = "abcdefg:hijk"
    val parseResult = fastparse.parse(sourceString, CustomisedRole.key(_)) match {
      case Parsed.Success(value, _) => Right(value)
      case failure: Parsed.Failure => Left(failure)
    }
    parseResult.value.shouldBe("abcdefg")
  }

  "CustomisedRole.pair" should "parse a pair" in {
    val sourceString = "ssh_keys_bucket: bucket"
    val parseResult = fastparse.parse(sourceString, CustomisedRole.pair(_)) match {
      case Parsed.Success(value, _) => Right(value)
      case failure: Parsed.Failure => Left(failure)
    }
    parseResult.value.shouldBe(("ssh_keys_bucket", SingleParamValue("bucket")))
  }

  "CustomisedRole.parameters" should "parse a list of parameters" in {
    val sourceString = "ssh_keys_bucket: bucket, ssh_keys_prefix: Team"
    val parseResult = fastparse.parse(sourceString, CustomisedRole.parameters(_)) match {
      case Parsed.Success(value, _) => Right(value.toMap)
      case failure: Parsed.Failure => Left(failure)
    }
    parseResult.value.shouldBe(Map("ssh_keys_bucket" -> SingleParamValue("bucket"), "ssh_keys_prefix" -> SingleParamValue("Team")))
  }

  "CustomisedRole.formInputTextToVariables" should "parse a map of variables" in {
    val sourceData = CustomisedRole.formInputTextToVariables("ssh_keys_bucket: bucket, ssh_keys_prefix: Team")
    println(s"\n\n\n\n\n\n sourceData = $sourceData ")
    println(s" sourceData.value = ${sourceData.value} \n\n\n\n\n\n")
    sourceData.value.shouldBe(Map("ssh_keys_bucket" -> SingleParamValue("bucket"), "ssh_keys_prefix" -> SingleParamValue("Team")))
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
