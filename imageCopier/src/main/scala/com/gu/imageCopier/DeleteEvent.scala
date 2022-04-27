package com.gu.imageCopier

import io.circe._
import io.circe.parser.decode
import com.gu.imageCopier.attempt._
import io.circe.{ Decoder, HCursor }

import cats.syntax.either._

case class Ami(account: String, id: String)

object Ami {
  implicit val amiDecoder: Decoder[Ami] = new Decoder[Ami] {
    final def apply(c: HCursor): Decoder.Result[Ami] = {
      for {
        account <- c.downField("account").as[String]
        id <- c.downField("id").as[String]
      } yield {
        new Ami(account, id)
      }
    }
  }
}

case class DeleteEvent(amis: List[Ami])

object DeleteEvent {
  implicit val deleteEventDecoder: Decoder[DeleteEvent] = new Decoder[DeleteEvent] {
    final def apply(c: HCursor): Decoder.Result[DeleteEvent] = {
      for {
        amis <- c.downField("amis").as[List[Ami]]
      } yield {
        new DeleteEvent(amis)
      }
    }
  }

  def fromJsonString(json: String): Attempt[DeleteEvent] = decode[DeleteEvent](json).toAttempt(JsonParseFailure)
}

