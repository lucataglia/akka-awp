//package actorWithProbe
//
//import actorWithProbe.testkit.AWP
//import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
//
//object Foo extends App {
//  val system = ActorSystem("foo")
//  val awp = system.actorOf(Props[BarActor], "bar")
//
//  val foo: ActorRef = system.actorOf(Props(new FooActor() {
//
//    override implicit val awpSelf: ActorRef = awp
//  }), "foo")
//
//  foo ! "ciao"
//}
//case class FooActor() extends Actor with ActorLogging with AWP {
//
//  def receive: Receive = {
//    case msg =>
//      context.become(void)
//      self ! msg
//  }
//
//  def void: Receive = {
//    case msg =>
//      sender() ! "FOOao"
//      log.info(s"FOO -> $msg")
//  }
//}
//
//case class BarActor() extends Actor with ActorLogging {
//  def receive: Receive = {
//    case msg => log.info(s"BAAAAAR $msg")
//  }
//}
