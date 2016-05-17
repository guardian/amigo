package packer

import org.scalatest._
import models.{ MessagePart, AmiId }

class PackerOutputParserSpec extends FlatSpec with Matchers {

  it should "parse a line of Packer output" in {
    PackerOutputParser.parseLine("1455354962,,ui,say,ubuntu-wily-java8 output will be in this color.") should be(
      Some(PackerOutputParser.UiOutput("info", List(MessagePart("ubuntu-wily-java8 output will be in this color.", "black"))))
    )
    PackerOutputParser.parseLine("""1455355203,,ui,message,    ubuntu-wily-java8: Adding tag: "SourceAMI": "ami-cda312be"""") should be(
      Some(PackerOutputParser.UiOutput("info", List(MessagePart("""    ubuntu-wily-java8: Adding tag: "SourceAMI": "ami-cda312be"""", "black"))))
    )
  }

  it should "parse an AMI creation message" in {
    PackerOutputParser.parseLine("1455355212,ubuntu-wily-java8,artifact,0,id,eu-west-1:ami-ea76c599") should be(
      Some(PackerOutputParser.AmiCreated(AmiId("ami-ea76c599")))
    )
  }

  it should "return None if it doesn't recognise the input" in {
    PackerOutputParser.parseLine("yolo") should be(None)
  }

  it should "parse a line of coloured Packer output" in {
    PackerOutputParser.parseLine("123,,ui,message,\u001B[0;32mfoo\u001B[0m") should be(
      Some(PackerOutputParser.UiOutput("info", List(
        MessagePart("foo", "green")
      )))
    )
    PackerOutputParser.parseLine("123,,ui,message,foo\u001B[0;32mbar\u001B[0m") should be(
      Some(PackerOutputParser.UiOutput("info", List(
        MessagePart("foo", "black"),
        MessagePart("bar", "green")
      )))
    )
    PackerOutputParser.parseLine("123,,ui,message,foo\u001B[0;32mbar\u001B[0mbaz") should be(
      Some(PackerOutputParser.UiOutput("info", List(
        MessagePart("foo", "black"),
        MessagePart("bar", "green"),
        MessagePart("baz", "black")
      )))
    )
    PackerOutputParser.parseLine("123,,ui,message,    grid-xenial: \u001B[0;33m127.0.0.1\u001B[0m                  : \u001B[0;32mok\u001B[0m\u001B[0;32m=\u001B[0m\u001B[0;32m9\u001B[0m    \u001B[0;33mchanged\u001B[0m\u001B[0;33m=\u001B[0m\u001B[0;33m7\u001B[0m    unreachable=0    failed=0") should be(
      Some(PackerOutputParser.UiOutput("info", List(
        MessagePart("    grid-xenial: ", "black"),
        MessagePart("127.0.0.1", "yellow"),
        MessagePart("                  : ", "black"),
        MessagePart("ok", "green"),
        MessagePart("=", "green"),
        MessagePart("9", "green"),
        MessagePart("    ", "black"),
        MessagePart("changed", "yellow"),
        MessagePart("=", "yellow"),
        MessagePart("7", "yellow"),
        MessagePart("    unreachable=0    failed=0", "black")
      )))
    )
  }

}
