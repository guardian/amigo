package packer

import models.AmiId

object PackerOutputParser {

  sealed abstract class PackerEvent
  case class UserFacingOutput(string: String) extends PackerEvent
  case class AmiCreated(amiId: AmiId) extends PackerEvent

  val UserFacingOutputRegex = """^\d+,,ui,.*,(.*)$""".r
  val AmiCreatedRegex = """^\d+,.*,artifact,\d+,id,[a-z0-9-]*:(.*)$""".r

  def parseLine(line: String): Option[PackerEvent] = line match {
    // TODO replace commas, parse ANSI colours
    case UserFacingOutputRegex(message) => Some(UserFacingOutput(message))
    case AmiCreatedRegex(amiId) => Some(AmiCreated(AmiId(amiId)))
    case _ => None
  }

}
