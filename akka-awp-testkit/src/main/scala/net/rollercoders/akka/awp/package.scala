package net.rollercoders.akka

import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  OneForOneStrategy,
  Props,
  Stash,
  SupervisorStrategy,
  Terminated
}
import akka.testkit.TestProbe

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag

package object awp {

  private val DEFAULT_SECONDS = 5
  private val DEFAULT_HINT = ""

  object ActorWithProbe {
    protected class ActorWithProbeBuilder(realActor: RealActor,
                                          maybeName: Option[String] = None,
                                          verbose: Boolean = true) {
      def withoutVerbose(): ActorWithProbeBuilder =
        new ActorWithProbeBuilder(realActor, maybeName, false)

      def withName(name: String): ActorWithProbeBuilder =
        new ActorWithProbeBuilder(realActor, Some(name), verbose)

      def build()(implicit system: ActorSystem): ActorWithProbe =
        privateActorOf(realActor, maybeName, verbose)
    }

    // Factory Methods
    def actorOf(f: ActorRef => Props)(implicit system: ActorSystem): ActorWithProbeBuilder =
      new ActorWithProbeBuilder(FromProps(f))

    def actorOf(props: Props)(implicit system: ActorSystem): ActorWithProbeBuilder =
      new ActorWithProbeBuilder(FromProps(_ => props))

    def actorOf(actorRef: ActorRef)(implicit system: ActorSystem): ActorWithProbeBuilder =
      new ActorWithProbeBuilder(FromRef(actorRef))

    private def privateActorOf(realActor: RealActor, maybeName: Option[String], verbose: Boolean)(
        implicit system: ActorSystem
    ): ActorWithProbe = {
      val (coreRef, probe) = maybeName match {
        case Some(name) =>
          val probe = TestProbe(s"probe-$name")
          val coreRef = system.actorOf(ActorWithProbeCore.props(realActor, probe, maybeName, verbose), s"awp-$name")
          (coreRef, probe)

        case None =>
          val probe = TestProbe()
          val coreRef = system.actorOf(ActorWithProbeCore.props(realActor, probe, maybeName, verbose))
          (coreRef, probe)
      }

      ActorWithProbe(coreRef, probe)
    }
  }

  // Actor Enhancer
  trait AWP {
    this: Actor =>
    implicit val awpSelf: ActorRef
  }

  // API
  case class ActorWithProbe(ref: ActorRef, probe: TestProbe) {
    def !(msg: Any)(implicit sender: ActorRef, system: ActorSystem): ActorWithWaitingFor =
      tell(msg, sender)

    def tell(msg: Any, sender: ActorRef)(implicit system: ActorSystem): ActorWithWaitingFor = {
      val awp = ActorWithProbe.actorOf(sender).build()
      ref tell (msg, awp)

      ActorWithWaitingFor(awp)
    }
  }

  // TestKit Enhancer (declarative programming)
  protected case class ActorWithWaitingFor(me: ActorWithProbe) {
    // Me
    def thenWaitMeReceiving(semaphoreMsg: Any,
                            maxSeconds: Int = DEFAULT_SECONDS,
                            hint: String = DEFAULT_HINT): ActorWithWaitingFor = {
      ActorWithReceiving(me, this).receiving(semaphoreMsg, maxSeconds, hint)
      this
    }

    def andThenWaitMeReceiving(semaphoreMsg: Any,
                               maxSeconds: Int = DEFAULT_SECONDS,
                               hint: String = DEFAULT_HINT): ActorWithWaitingFor = {
      ActorWithReceiving(me, this).receiving(semaphoreMsg, maxSeconds, hint)
      this
    }

    def thenWaitMeReceivingType[T](implicit msgType: ClassTag[T]): ActorWithWaitingFor =
      thenWaitMeReceivingType()

    def thenWaitMeReceivingType[T](maxSeconds: Int = DEFAULT_SECONDS, hint: String = DEFAULT_HINT)(
        implicit msgType: ClassTag[T]): ActorWithWaitingFor = {
      ActorWithReceiving(me, this).receivingType(maxSeconds, hint)
      this
    }

    def andThenWaitMeReceivingType[T](implicit msgType: ClassTag[T]): ActorWithWaitingFor =
      andThenWaitMeReceivingType[T]()

    def andThenWaitMeReceivingType[T](maxSeconds: Int = DEFAULT_SECONDS, hint: String = DEFAULT_HINT)(
        implicit msgType: ClassTag[T]): ActorWithWaitingFor = {
      ActorWithReceiving(me, this).receivingType[T](maxSeconds, hint)
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

  protected case class ActorWithReceiving(awpReceiver: ActorWithProbe, awwf: ActorWithWaitingFor) {
    def receiving(semaphoreMsg: Any,
                  maxSeconds: Int = DEFAULT_SECONDS,
                  hint: String = DEFAULT_HINT): ActorWithWaitingFor = {
      awpReceiver.eventuallyReceiveMsg(semaphoreMsg, maxSeconds, hint)
      awwf
    }

    def receivingType[T](implicit msgType: ClassTag[T]): ActorWithWaitingFor =
      receivingType[T]()

    def receivingType[T](maxSeconds: Int = DEFAULT_SECONDS, hint: String = DEFAULT_HINT)(
        implicit msgType: ClassTag[T]): ActorWithWaitingFor = {
      awpReceiver.eventuallyReceiveMsgType[T](maxSeconds, hint)
      awwf
    }
  }

  protected case class ActorWithAllReceiving(awpReceivers: List[ActorWithProbe], awwf: ActorWithWaitingFor) {
    def receiving(semaphoreMsg: Any,
                  maxSeconds: Int = DEFAULT_SECONDS,
                  hint: String = DEFAULT_HINT): ActorWithWaitingFor = {
      awpReceivers foreach (_.eventuallyReceiveMsg(
        semaphoreMsg,
        maxSeconds,
        hint
      ))
      awwf
    }

    def receivingType[T](implicit msgType: ClassTag[T]): ActorWithWaitingFor =
      receivingType[T]()

    def receivingType[T](maxSeconds: Int = DEFAULT_SECONDS, hint: String = DEFAULT_HINT)(
        implicit msgType: ClassTag[T]): ActorWithWaitingFor = {
      awpReceivers foreach (_.eventuallyReceiveMsgType[T](
        maxSeconds,
        hint
      ))
      awwf
    }
  }

  // Syntactic sugar
  implicit def awpToActorRef(awp: ActorWithProbe): ActorRef = awp.ref
  implicit def awpToTestProbe(awp: ActorWithProbe): TestProbe = awp.probe
  implicit def awpToEnrichActorWithProbe(awp: ActorWithProbe): EnrichActorWithProbe = EnrichActorWithProbe(awp)

  protected case class EnrichActorWithProbe(awp: ActorWithProbe) {
    def eventuallyReceiveMsg(msg: Any, maxSeconds: Int = DEFAULT_SECONDS, hint: String = DEFAULT_HINT): Any = {
      val hintOrElse =
        if (hint.isEmpty)
          s"${awp.probe.ref} waiting for ${msg.getClass}"
        else hint

      awp.probe.fishForMessage(hint = hintOrElse, max = maxSeconds seconds) {
        case received =>
          received == msg
      }
    }

    def eventuallyReceiveMsgType[T](implicit msgType: ClassTag[T]): Unit =
      eventuallyReceiveMsgType[T]()

    def eventuallyReceiveMsgType[T](maxSeconds: Int = DEFAULT_SECONDS, hint: String = DEFAULT_HINT)(
        implicit msgType: ClassTag[T]): Unit = {
      val hintOrElse =
        if (hint.isEmpty)
          s"${awp.probe.ref} waiting for ${msgType.runtimeClass}"
        else hint

      awp.probe.fishForMessage(hint = hintOrElse, max = maxSeconds seconds) {
        case received =>
          msgType.runtimeClass.isInstance(received)

      }
    }
  }

  // Core
  private sealed trait RealActor
  private case class FromProps(f: ActorRef => Props) extends RealActor
  private case class FromRef(actorRef: ActorRef) extends RealActor
  private class ActorWithProbeCore(realActor: RealActor, probe: TestProbe, maybeName: Option[String], verbose: Boolean)
      extends Actor
      with ActorLogging
      with Stash {
    import ActorWithProbeCore.InitAWP

    private val lPrefix = "[AWP]".withCyan
    private val lReceiver = "final receiver".withUnderline
    private val lSender = "sender".withUnderline
    private val lStash = "[STASHED]".withCyan
    private val lSupervisor = "[SUPERVISOR]".withCyan
    private val lTerminated = "[TERMINATED]".withCyan

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case msg =>
        log.info(s"$lSupervisor received $msg")
        SupervisorStrategy.defaultDecider(msg)
    }

    override def preStart(): Unit = {
      val realActorRef = realActor match {
        case FromProps(f) =>
          maybeName match {
            case Some(name) => context.actorOf(f(self), s"real-$name")
            case None       => context.actorOf(f(self))
          }
        case FromRef(ref) => ref
      }

      context.watch(realActorRef)

      self ! InitAWP(realActorRef, probe)
    }

    def receive: Receive = waiting()

    def waiting(): Receive = {
      case Terminated(ref) =>
        log.info(s"$lTerminated real actor ref ${ref.path} is terminated")
        context.stop(self)

      case InitAWP(ref, probe) =>
        if (verbose)
          log.info(s"$lPrefix InitAWP")

        context.become(running(ref, probe))
        unstashAll()

      case msg =>
        if (verbose)
          log.info(s"$lStash ${msg.toString.withGreen} - $lSender ${sender.path}")

        stash()
    }

    def running(ref: ActorRef, probe: TestProbe): Receive = {
      case Terminated(ref) =>
        log.info(s"$lTerminated real actor ref ${ref.path} is terminated")
        context.stop(self)

      case msg =>
        if (verbose)
          log.info(s"$lPrefix ${msg.toString.withGreen} - $lSender ${sender.path} $lReceiver ${ref.path}")

        probe.ref forward msg
        ref forward msg
    }
  }

  private object ActorWithProbeCore {

    case class InitAWP(actorRef: ActorRef, probe: TestProbe)

    def props(realActor: RealActor, probe: TestProbe, maybeName: Option[String], verbose: Boolean): Props =
      Props(new ActorWithProbeCore(realActor, probe, maybeName, verbose))
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
