package packer

import models.AmiId

trait PackerListener {

  def onLineOfOutput(string: String): Unit

  def onAmiCreated(amiId: AmiId): Unit

  def onProcessExited(exitCode: Int): Unit

}
