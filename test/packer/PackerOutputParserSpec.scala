package packer

import models.{AmiId, MessagePart}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PackerOutputParserSpec extends AnyFlatSpec with Matchers {

  it should "parse a line of Packer output" in {
    PackerOutputParser.parseLine(
      "1455354962,,ui,say,ubuntu-wily-java8 output will be in this color."
    ) should be(
      Some(
        PackerOutputParser.UiOutput(
          "info",
          List(
            MessagePart(
              "ubuntu-wily-java8 output will be in this color.",
              "#DCDCDC"
            )
          )
        )
      )
    )
    PackerOutputParser.parseLine(
      """1455355203,,ui,message,    ubuntu-wily-java8: Adding tag: "SourceAMI": "ami-cda312be""""
    ) should be(
      Some(
        PackerOutputParser.UiOutput(
          "info",
          List(
            MessagePart(
              """    ubuntu-wily-java8: Adding tag: "SourceAMI": "ami-cda312be"""",
              "#DCDCDC"
            )
          )
        )
      )
    )
  }

  it should "parse an AMI creation message" in {
    PackerOutputParser.parseLine(
      "1455355212,ubuntu-wily-java8,artifact,0,id,eu-west-1:ami-ea76c599"
    ) should be(Some(PackerOutputParser.AmiCreated(AmiId("ami-ea76c599"))))
  }

  it should "return None if it doesn't recognise the input" in {
    PackerOutputParser.parseLine("yolo") should be(None)
  }

  it should "parse a line of coloured Packer output" in {
    PackerOutputParser.parseLine(
      "123,,ui,message,\u001B[0;32mfoo\u001B[0m"
    ) should be(
      Some(
        PackerOutputParser.UiOutput("info", List(MessagePart("foo", "#00C200")))
      )
    )
    PackerOutputParser.parseLine(
      "123,,ui,message,foo\u001B[0;32mbar\u001B[0m"
    ) should be(
      Some(
        PackerOutputParser.UiOutput(
          "info",
          List(MessagePart("foo", "#DCDCDC"), MessagePart("bar", "#00C200"))
        )
      )
    )
    PackerOutputParser.parseLine(
      "123,,ui,message,foo\u001B[0;32mbar\u001B[0mbaz"
    ) should be(
      Some(
        PackerOutputParser.UiOutput(
          "info",
          List(
            MessagePart("foo", "#DCDCDC"),
            MessagePart("bar", "#00C200"),
            MessagePart("baz", "#DCDCDC")
          )
        )
      )
    )
    PackerOutputParser.parseLine(
      "123,,ui,message,    grid-xenial: \u001B[0;33m127.0.0.1\u001B[0m                  : \u001B[0;32mok\u001B[0m\u001B[0;32m=\u001B[0m\u001B[0;32m9\u001B[0m    \u001B[0;33mchanged\u001B[0m\u001B[0;33m=\u001B[0m\u001B[0;33m7\u001B[0m    unreachable=0    failed=0"
    ) should be(
      Some(
        PackerOutputParser.UiOutput(
          "info",
          List(
            MessagePart("    grid-xenial: ", "#DCDCDC"),
            MessagePart("127.0.0.1", "#C7C400"),
            MessagePart("                  : ", "#DCDCDC"),
            MessagePart("ok", "#00C200"),
            MessagePart("=", "#00C200"),
            MessagePart("9", "#00C200"),
            MessagePart("    ", "#DCDCDC"),
            MessagePart("changed", "#C7C400"),
            MessagePart("=", "#C7C400"),
            MessagePart("7", "#C7C400"),
            MessagePart("    unreachable=0    failed=0", "#DCDCDC")
          )
        )
      )
    )
  }

  it should "parse a multi-line string of coloured Ansible output" in {
    PackerOutputParser.parseLine(
      "123,,ui,message,\u001B[0;33mchanged: [127.0.0.1] => {\"changed\": true, \"msg\": \"Reading package lists...\\nBuilding dependency tree...\"}\u001B[0m"
    ) should be(
      Some(
        PackerOutputParser.UiOutput(
          "info",
          List(
            MessagePart(
              "changed: [127.0.0.1] => {\"changed\": true, \"msg\": \"Reading package lists...\nBuilding dependency tree...\"}",
              "#C7C400"
            )
          )
        )
      )
    )
  }

}
