package net.rollercoders.akka.awp

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import net.rollercoders.akka.awp.TestActors.PingActor.Pong
import net.rollercoders.akka.awp.TestActors.PongActor.Ping
import net.rollercoders.akka.awp.TestActors.SillyActor.Envelop
import net.rollercoders.akka.awp.TestActors.TimerActor.{Start, Stop}
import net.rollercoders.akka.awp.TestActors.{PingActor, PongActor, SillyActor, TimerActor}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

class TestActorSpec
    extends TestKit(ActorSystem("awp"))
    with ImplicitSender
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "A Ping Pong network" must {
    "send a Ping message to Pong Actor " in {
      val pongRef = ActorWithProbe.actorOf(Props[PongActor]).withName("pong-1").build()
      val pingRef = ActorWithProbe.actorOf(PingActor.props(pongRef)).withName("ping-1").build()

      // Low level API
      pingRef ! "ping"
      pongRef eventuallyReceiveMsg Ping

      pingRef ! "ping"
      pongRef.eventuallyReceiveMsgType[Ping.type]

      // High level API
      pingRef ! "ping" thenWaitFor pongRef receiving Ping
      pingRef.!("ping").thenWaitFor(pongRef).receivingType[Ping.type]

      // TestProbe API
      pingRef ! "ping"
      pongRef expectMsg Ping
    }

    "send a Ping message and then have a Pong answer" in {
      val pongRef = ActorWithProbe.actorOf(Props[PongActor]).withName("pong-2").build()
      val pingRef =
        ActorWithProbe
          .actorOf(
            ref =>
              Props(new PingActor(pongRef) {
                override implicit val awpSelf: ActorRef = ref
              })
          )
          .withName("ping-2")
          .build()

      // Low level API
      pingRef ! "ping"
      pongRef eventuallyReceiveMsg Ping
      pingRef eventuallyReceiveMsg Pong

      pingRef ! "ping"
      pongRef.eventuallyReceiveMsgType[Ping.type]
      pingRef.eventuallyReceiveMsgType[Pong.type]

      // High level API
      pingRef ! "ping" thenWaitFor pongRef receiving Ping andThenWaitFor pingRef receiving Pong
      pingRef.!("ping").thenWaitFor(pongRef).receivingType[Ping.type]().andThenWaitFor(pingRef).receivingType[Pong.type]

      // TestProbe API
      pingRef ! "ping"
      pingRef expectMsg "ping"
      pongRef expectMsg Ping
      pingRef expectMsg Pong
    }
  }

  "An actor that send to itself a message" must {
    "be able to test if he received it" in {

      val sillyRef =
        ActorWithProbe
          .actorOf(
            ref =>
              Props(new SillyActor("Got the message") {
                override implicit val awpSelf: ActorRef = ref
              })
          )
          .withName("silly")
          .build()

      // Low level API
      sillyRef ! "Hello akka-awp"
      sillyRef eventuallyReceiveMsg Envelop("Hello akka-awp")

      sillyRef ! "Hello akka-awp"
      sillyRef.eventuallyReceiveMsgType[Envelop]

      // High level API
      sillyRef ! "Hello akka-awp" thenWaitFor sillyRef receiving Envelop("Hello akka-awp") andThenWaitMeReceiving "Got the message"
      sillyRef.!("Hello akka-awp").thenWaitFor(sillyRef).receivingType[Envelop].andThenWaitMeReceivingType[String]

      // TestProbe API
      sillyRef ! "Hello akka-awp"
      sillyRef expectMsg "Hello akka-awp"
      sillyRef expectMsg Envelop("Hello akka-awp")
    }
  }

  "An actor that has a periodic timer" must {
    "receive all the messages since it stopped" in {
      import system.dispatcher

      import scala.concurrent.duration._

      val msg = "Hello Scheduler !!!"

      val timerRef =
        ActorWithProbe
          .actorOf(
            ref =>
              Props(new TimerActor(msg) {
                override implicit val awpSelf: ActorRef = ref
              })
          )
          .withName("timer")
          .build()

      timerRef ! Start
      system.scheduler.scheduleOnce(5 seconds) {
        timerRef ! Stop
      }

      timerRef eventuallyReceiveMsg Start
      // schedulerRef eventuallyReceiveMsg msg // This won't work since can't be overridden the self used by Timers
      timerRef.eventuallyReceiveMsg(Stop, 6)
    }
  }
}
