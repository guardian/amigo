package packer

import models.{ MessagePart, AmiId }

object PackerOutputParser {

  sealed abstract class PackerEvent
  case class UiOutput(logLevel: String, messageParts: List[MessagePart]) extends PackerEvent
  case class AmiCreated(amiId: AmiId) extends PackerEvent

  val UiOutputRegex = """^\d+,,ui,(.*),(.*)$""".r
  val AmiCreatedRegex = """^\d+,.*,artifact,\d+,id,[a-z0-9-]*:(.*)$""".r

  def parseLine(line: String): Option[PackerEvent] = line match {
    case UiOutputRegex(messageType, output) => Some(UiOutput(toLogLevel(messageType), parseUiOutput(output)))
    case AmiCreatedRegex(amiId) => Some(AmiCreated(AmiId(amiId)))
    case _ => None
  }

  // Message type will be one of "say", "message" or "error".
  // Not really sure of the difference between say and message.
  private def toLogLevel(messageType: String) = messageType match {
    case "error" => "error"
    case _ => "info"
  }

  private def parseUiOutput(output: String): List[MessagePart] = {
    val substituted = output
      .replaceAllLiterally("%!(PACKER_COMMA)", ",")
      .replaceAllLiterally("\\n", "\n")
      .replaceAllLiterally("\\r", "\r")

    // TODO parse ANSI colours
    List(MessagePart(substituted, "black"))
  }
}
