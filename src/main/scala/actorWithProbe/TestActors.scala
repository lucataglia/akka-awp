package actorWithProbe

import actorWithProbe.testkit.AWP
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object TestActors {
  // - - - PING PONG - - -
  class PingActor(pong: ActorRef) extends Actor with ActorLogging with AWP {
    import PingActor._
    import PongActor._

    def receive: Receive = {
      case msg @ "ping" =>
        log.info(msg)
        pong ! Ping

      case msg @ Pong =>
        log.info(s"$msg")
    }

    override implicit val awpSelf: ActorRef = self
  }

  object PingActor {
    case object Pong
    def props(pongRef: ActorRef): Props = Props(new PingActor(pongRef))
  }

  class PongActor() extends Actor with ActorLogging with AWP {
    import PongActor._
    import PingActor._

    def receive: Receive = {
      case msg @ Ping =>
        log.info(s"$msg")
        sender() ! Pong

      case msg =>
        log.info(s"$msg")
    }

    override implicit val awpSelf: ActorRef = self
  }

  object PongActor {
    case object Ping
    def props(): Props = Props[PongActor]
  }

  // - - - ACTOR THAT SEND A MESSAGE TO ITSELF - - -
  class SelfActor() extends Actor with ActorLogging with AWP {
    import SelfActor._

    def receive: Receive = {
      case msg @ Envelop(_) =>
        log.info(s"$msg")

      case msg =>
        log.info(s"$msg")
        awpSelf ! Envelop(msg)
    }

    override implicit val awpSelf: ActorRef = self
  }

  object SelfActor {
    case class Envelop(msg: Any)
  }

}
