package actorWithProbe

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import akka.util.BoxedType

import scala.concurrent.duration._
import scala.language.implicitConversions

package object testkit {

  import ActorWithProbeCore.InitAWP

  // Factory Method
  object ActorWithProbe {
    def actorOf(f: ActorRef => Props, name: String, verbose: Boolean)(
        implicit system: ActorSystem
    ): ActorWithProbe = {
      val probe = TestProbe(s"probe-$name")
      val swp = system.actorOf(ActorWithProbeCore.props(verbose), s"swp-$name")
      val realActorRef = system.actorOf(f(swp), s"real-$name")

      swp ! InitAWP(realActorRef, probe)
      ActorWithProbe(swp, probe)
    }

    def actorOf(props: Props, name: String, verbose: Boolean)(
        implicit system: ActorSystem
    ): ActorWithProbe = {
      val probe = TestProbe(s"probe-$name")
      val awp = system.actorOf(ActorWithProbeCore.props(verbose), s"awp-$name")
      val realActorRef = system.actorOf(props, s"real-$name")

      awp ! InitAWP(realActorRef, probe)
      ActorWithProbe(awp, probe)
    }
  }

  // API
  case class ActorWithProbe(ref: ActorRef, probe: TestProbe) {
    private val awwf: ActorWithWaitingFor = ActorWithWaitingFor(this)

    def !(msg: Any)(implicit sender: ActorRef): ActorWithWaitingFor =
      tell(msg, sender)

    def tell(msg: Any, sender: ActorRef): ActorWithWaitingFor = {
      ref tell (msg, sender)
      awwf
    }
  }

  // Syntactic sugar
  implicit def awpToActorRef(awp: ActorWithProbe): ActorRef = awp.ref

  implicit class EnrichActorWithProbe(awp: ActorWithProbe) {
    def eventuallyReceiveMsg(msg: Any, maxSeconds: Int = 5, hint: String = ""): Any = {
      val hintOrElse =
        if (hint.isEmpty)
          s"${awp.probe.ref} waiting for ${msg.getClass}"
        else hint

      awp.probe.fishForMessage(hint = hintOrElse, max = maxSeconds seconds) {
        case received =>
          received == msg
      }
    }
  }

  // TestKit Enhancer (declarative programming)
  case class ActorWithWaitingFor(me: ActorWithProbe) {
    // Me
    def thenWaitMeReceiving(semaphoreMsg: Any, maxSeconds: Int = 5, hint: String = ""): ActorWithWaitingFor = {
      ActorWithReceiving(me, this).receiving(semaphoreMsg, maxSeconds, hint)
      this
    }

    def andThenWaitMeReceiving(semaphoreMsg: Any, maxSeconds: Int = 5, hint: String = ""): ActorWithWaitingFor = {
      ActorWithReceiving(me, this).receiving(semaphoreMsg, maxSeconds, hint)
      this
    }

    // Other
    def thenWaitFor(awpReceiver: ActorWithProbe): ActorWithReceiving =
      ActorWithReceiving(awpReceiver, this)

    def andThenWaitFor(awpReceiver: ActorWithProbe): ActorWithReceiving =
      ActorWithReceiving(awpReceiver, this)

    def thenWaitForAll(
        awpReceivers: List[ActorWithProbe]
    ): ActorWithAllReceiving =
      ActorWithAllReceiving(awpReceivers, this)

    def andThenWaitForAll(
        awpReceivers: List[ActorWithProbe]
    ): ActorWithAllReceiving =
      ActorWithAllReceiving(awpReceivers, this)
  }

  case class ActorWithReceiving(awpReceiver: ActorWithProbe, awwf: ActorWithWaitingFor) {
    def receiving(semaphoreMsg: Any, maxSeconds: Int = 5, hint: String = ""): ActorWithWaitingFor = {
      awpReceiver.eventuallyReceiveMsg(semaphoreMsg, maxSeconds, hint)
      awwf
    }
  }

  case class ActorWithAllReceiving(awpReceivers: List[ActorWithProbe], awwf: ActorWithWaitingFor) {
    def receiving(semaphoreMsg: Any, maxSeconds: Int = 5, hint: String = ""): ActorWithWaitingFor = {
      awpReceivers foreach (_.eventuallyReceiveMsg(
        semaphoreMsg,
        maxSeconds,
        hint
      ))
      awwf
    }
  }

  // Core
  class ActorWithProbeCore(verbose: Boolean) extends Actor with ActorLogging {
    private val prefix = "[AWP]".withCyan
    private val sSender = "sender".withUnderline
    private val sReceiver = "final receiver".withUnderline

    def receive: Receive = waiting()

    def waiting(): Receive = {
      case InitAWP(ref, probe) =>
        if (verbose) log.info(s"$prefix InitAWP")
        context.become(running(ref, probe))
    }

    def running(ref: ActorRef, probe: TestProbe): Receive = {
      case msg =>
        if (verbose)
          log.info(s"$prefix ${msg.toString.withGreen} - $sSender ${sender.path} $sReceiver ${ref.path}")

        probe.ref forward msg
        ref forward msg
    }
  }

  object ActorWithProbeCore {

    case class InitAWP(actorRef: ActorRef, probe: TestProbe)

    def props(verbose: Boolean): Props = Props(new ActorWithProbeCore(verbose))
  }

  trait AWP {
    this: Actor =>
    implicit val awpSelf: ActorRef
  }

  private implicit class StringWithColors(str: String) {
    val CYAN = "\u001b[0;36m"
    val GREEN = "\u001b[0;32m"
    val NC = "\u001b[0;0m"
    val UNDERLINE = "\u001b[0004m"

    def withCyan: String = s"$CYAN$str$NC"
    def withGreen: String = s"$GREEN$str$NC"
    def withUnderline: String = s"$UNDERLINE$str$NC"
  }
}
