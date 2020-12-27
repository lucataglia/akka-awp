package net.rollercoders.akka.awp

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}

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
    import PingActor._
    import PongActor._

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
  class SillyActor(answer: String) extends Actor with ActorLogging with AWP {
    import SillyActor._

    def receive: Receive = {
      case msg @ Envelop(_) =>
        log.info(s"$msg")
        sender() ! answer

      case msg =>
        log.info(s"$msg")
        awpSelf forward Envelop(msg)
    }

    override implicit val awpSelf: ActorRef = self
  }

  object SillyActor {
    case class Envelop(msg: Any)
  }

  class TimerActor(msg: Any) extends Actor with ActorLogging with Timers with AWP {
    import TimerActor.{Start, Stop, TimerKey}

    import scala.concurrent.duration._

    override def receive: Receive = {
      case Start =>
        log.info(s"[Start]")
        timers.startTimerAtFixedRate(TimerKey, msg, 1 second)

      case Stop =>
        log.info(s"[Stop]")
        context.stop(self)

      case msg =>
        log.info(s"$msg")
    }

    override implicit val awpSelf: ActorRef = self
  }

  object TimerActor {
    case object TimerKey
    case object Start
    case object Stop
  }

}
