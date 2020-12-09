package actorWithProbe

import actorWithProbe.TestActors.PingActor.Pong
import actorWithProbe.TestActors.PongActor.Ping
import actorWithProbe.TestActors.SelfActor.Envelop
import actorWithProbe.TestActors.{PingActor, PongActor, SelfActor}
import actorWithProbe.testkit._
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{MustMatchers, WordSpecLike}

class TestActorSpec extends TestKit(ActorSystem("awp")) with ImplicitSender with WordSpecLike with MustMatchers {

  "A Ping Pong network" must {
    "send a Ping message to Pong Actor " in {
      val pongRef = ActorWithProbe.actorOf(PongActor.props(), "pong-1", verbose = false)
      val pingRef = ActorWithProbe.actorOf(PingActor.props(pongRef), "ping-1", verbose = false)

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
      val pongRef = ActorWithProbe.actorOf(PongActor.props(), "pong-2", verbose = false)
      val pingRef =
        ActorWithProbe.actorOf(
          ref =>
            Props(new PingActor(pongRef) {
              override implicit val awpSelf: ActorRef = ref;
            }),
          "ping-2",
          verbose = false
        )

      // Low level API
      pingRef ! "ping"
      pongRef eventuallyReceiveMsg Ping
      pingRef eventuallyReceiveMsg Pong

      pingRef ! "ping"
      pongRef.eventuallyReceiveMsgType[Ping.type]
      pingRef.eventuallyReceiveMsgType[Pong.type]

      // High level API
      pingRef ! "ping" thenWaitFor pongRef receiving Ping andThenWaitMeReceiving Pong
      pingRef.!("ping").thenWaitFor(pongRef).receivingType[Ping.type]().andThenWaitMeReceivingType[Pong.type]

      // TestProbe API
      pingRef ! "ping"
      pingRef expectMsg "ping"
      pongRef expectMsg Ping
      pingRef expectMsg Pong
    }
  }

  "An actor that send to itself a message" must {
    "be able to test if he received it" in {

      val selfRef =
        ActorWithProbe.actorOf(
          ref =>
            Props(new SelfActor() {
              override implicit val awpSelf: ActorRef = ref;
            }),
          "self-3",
          verbose = true
        )

      // Low level API
      selfRef ! "Hello Jeff !!"
      selfRef eventuallyReceiveMsg Envelop("Hello Jeff !!")

      selfRef ! "Hello Jeff !!"
      selfRef.eventuallyReceiveMsgType[Envelop]

      // High level API
      selfRef ! "Hello Jeff !!" thenWaitMeReceiving Envelop("Hello Jeff !!")
      selfRef.!("Hello Jeff !!").thenWaitMeReceivingType[Envelop]

      // TestProbe API
      selfRef ! "Hello Jeff !!"
      selfRef expectMsg "Hello Jeff !!"
      selfRef expectMsg Envelop("Hello Jeff !!")
    }
  }
}
