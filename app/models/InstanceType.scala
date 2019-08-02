package models

case class InstanceType(name: String) {
  override def toString: String = this.name
}

object InstanceType {
  val DEFAULT: InstanceType = T3Micro
}

private object T3Micro extends InstanceType("t3.micro")
