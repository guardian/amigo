package models

import org.joda.time.DateTime
import play.twirl.api.Html

case class MessagePart(text: String, colour: String) {

  def toHtml = {
    val htmlColour = MessagePart.HtmlColours.getOrElse(colour, colour)
    s"""<span style="color: $htmlColour">$text</span>"""
  }

}

object MessagePart {

  val HtmlColours = Map(
    "yellow" -> "#FF9900" // kinda orangey
  )

}

case class BakeLog(bakeId: BakeId, logNumber: Int, timestamp: DateTime, logLevel: String, messageParts: List[MessagePart]) {

  def messageHtml = Html(messageParts.map(_.toHtml).mkString)

}

