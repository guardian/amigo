package event

import play.api.Logger
import play.api.libs.iteratee.Concurrent.Channel

trait EventBus {

  def publish(event: BakeEvent): Unit

}

class ChannelWrapper(channel: Channel[BakeEvent]) extends EventBus {
  import BakeEvent._

  def publish(dataMessage: BakeEvent): Unit = {
    channel.push(dataMessage)
    log(dataMessage)
  }

  private def log(event: BakeEvent) = event match {
    case Log(bakeId, line) => Logger.info(s"PACKER: $line")
    case AmiCreated(bakeId, amiId) => Logger.info(s"Packer created an AMI! AMI id = ${amiId.value}")
    case PackerProcessExited(bakeId, exitCode) => Logger.info(s"Packer process completed with exit code $exitCode")
  }

}
