package models

import org.joda.time.DateTime
import play.twirl.api.Html

case class MessagePart(text: String, colour: String) {

  def toHtml = s"""<span style="color: $colour">$text</span>"""

}

case class BakeLog(bakeId: BakeId, logNumber: Int, timestamp: DateTime, logLevel: String, messageParts: List[MessagePart]) {

  def messageHtml = Html(messageParts.map(_.toHtml).mkString)

}

