package net.rollercoders.akka.awp.`trait`

import akka.actor.{Actor, ActorRef}

// Actor Enhancer
trait AWP {
  this: Actor =>
  implicit val awpSelf: ActorRef
}
