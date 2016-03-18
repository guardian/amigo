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

}
