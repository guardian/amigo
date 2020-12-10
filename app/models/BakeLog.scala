package models

import org.joda.time.DateTime
import play.twirl.api.Html

case class MessagePart(text: String, colour: String) {

  def toHtml = {
    val htmlColour = MessagePart.HtmlColours.getOrElse(colour, colour)
    s"""<span style="color: $htmlColour">${text.replaceAllLiterally("\n", "<br>")}</span>"""
  }

}

object MessagePart {
  val defaultColour = "#DCDCDC" /* iTerm2's default theme */

  val HtmlColours = Map(
    "yellow" -> "#ECE100" /* iTerm2's default theme */
  )

}

case class BakeLog(bakeId: BakeId, logNumber: Int, timestamp: DateTime, logLevel: String, messageParts: List[MessagePart]) {

  def messageHtml = Html(messageParts.map(_.toHtml).mkString)

}

