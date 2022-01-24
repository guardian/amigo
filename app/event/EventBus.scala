package event

import akka.actor.typed.ActorSystem

trait EventBus {

  def publish(event: BakeEvent): Unit

}

class ActorSystemWrapper(system: ActorSystem[BakeEvent]) extends EventBus {

  def publish(event: BakeEvent): Unit = system ! event

}
