package packer

import models.{ MessagePart, AmiId }

import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex.MatchIterator

object PackerOutputParser {

  sealed abstract class PackerEvent
  case class UiOutput(logLevel: String, messageParts: List[MessagePart]) extends PackerEvent
  case class AmiCreated(amiId: AmiId) extends PackerEvent

  private val UiOutputRegex = """^\d+,,ui,(.*),(.*)$""".r
  private val AmiCreatedRegex = """^\d+,.*,artifact,\d+,id,[a-z0-9-]*:(.*)$""".r

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

  // Matches a piece of text wrapped in ANSI codes, e.g. "\u001B[0;32mHello I am green\u001B[0m"
  private val AnsiColouredPart = "\u001B\\[0;(\\d{1,2})m(.*?)\u001B\\[0m".r

  private def parseUiOutput(output: String): List[MessagePart] = {
    val message = output
      .replaceAllLiterally("%!(PACKER_COMMA)", ",")
      .replaceAllLiterally("\\n", "\n")
      .replaceAllLiterally("\\r", "\r")

    parseAnsiColouredString(message).toList
  }

  /*
  Note: while this is not a full-blown ANSI code parser, it should handle anything Packer throws at it.
  Packer kindly terminates all coloured sections with a reset, which makes things slightly easier for us.
   */
  private def parseAnsiColouredString(message: String): Seq[MessagePart] = {
    val it = AnsiColouredPart.findAllIn(message)
    val parts = ArrayBuffer.empty[MessagePart]
    var previousEndIndex = 0

    while (it.hasNext) {
      it.next()
      if (it.start > previousEndIndex) {
        // Add any non-coloured part between the end of the previous coloured part
        // and the start of this one
        parts += MessagePart(message.substring(previousEndIndex, it.start), "black")
      }

      val colourCode = it.group(1)
      val colour = AnsiColours.getOrElse(colourCode, "black")
      val text = it.group(2)
      parts += MessagePart(text, colour)

      previousEndIndex = it.end
    }

    if (previousEndIndex < message.length) {
      // Add any non-coloured part after the final coloured part
      parts += MessagePart(message.substring(previousEndIndex), "black")
    }

    parts
  }

  // Only handle the foreground colours
  private val AnsiColours = Map(
    "31" -> "red",
    "32" -> "green",
    "33" -> "yellow",
    "34" -> "blue",
    "35" -> "magenta",
    "36" -> "cyan"
  )
}
