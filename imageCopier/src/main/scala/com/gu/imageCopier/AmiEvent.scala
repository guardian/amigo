package com.gu.imageCopier

import io.circe._
import io.circe.parser.decode
import com.gu.imageCopier.attempt._
import cats.syntax.either._

case class AmiEvent(sourceAmi: String, sourceRegion: String, targetAccounts: List[String], name: String, description: String, tags: Map[String, String])

object AmiEvent {
  implicit val amiEventDecoder: Decoder[AmiEvent] = new Decoder[AmiEvent] {
    final def apply(c: HCursor): Decoder.Result[AmiEvent] =
      for {
        sourceAmi <- c.downField("sourceAmi").as[String]
        sourceRegion <- c.downField("sourceRegion").as[String]
        targetAccounts <- c.downField("targetAccounts").as[List[String]]
        name <- c.downField("name").as[String]
        description <- c.downField("description").as[String]
        tags <- c.downField("tags").as[Map[String, String]]
      } yield {
        new AmiEvent(sourceAmi, sourceRegion, targetAccounts, name, description, tags)
      }
  }

  def fromJsonString(json: String): Attempt[AmiEvent] = decode[AmiEvent](json).toAttempt(JsonParseFailure)
}
