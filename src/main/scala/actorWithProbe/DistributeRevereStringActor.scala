package actorWithProbe

import actorWithProbe.ChildrenActor.Reverse
import actorWithProbe.testkit.AWP
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.routing.RoundRobinPool

class DistributeRevereStringActor(pool: Int) extends Actor with ActorLogging with Stash with AWP {
  import DistributeRevereStringActor._

  override def preStart(): Unit = {
    val router: ActorRef = context.actorOf(RoundRobinPool(pool).props(Props[ChildrenActor]))
    awpSelf ! Init(router)
  }

  override def receive: Receive = waiting()

  def waiting(): Receive = {
    case Init(router) =>
      log.info(s"[Init] poolSize: $pool")

      context.become(ready(router))
      unstashAll()

    case _ =>
      stash()
  }

  def ready(router: ActorRef, left: Int = 0, results: Vector[(String, Int)] = Vector.empty): Receive = {
    case Exec(str, part) =>
      log.info(s"[EXEC] Received long string to split in $part part")

      val len = str.length / part
      (0 until part)
        .foreach(index => {
          val start = len * index
          val end = if (index == part - 1) str.length else len * (index + 1)

          val subStr = str.substring(start, end)
          router ! Reverse(subStr, index)
        })

      context.become(ready(router, part, results))

    case Sorted(sorted, id) if left == 1 =>
      val result = (results :+ (sorted, id)).sortBy(_._2).reverse.map(_._1).mkString("")
      awpSelf ! Result(result)

    case Sorted(sorted, id) =>
      context.become(ready(router, left - 1, results :+ (sorted, id)))

    case Result(result) =>
      log.info(s"[RESULT] $result")
      context.become(ready(router))
  }

  override implicit val awpSelf: ActorRef = self
}

object DistributeRevereStringActor {
  case class Init(router: ActorRef)
  case class Exec(str: String, part: Int)
  case class Sorted(sorted: String, id: Int)
  case class Start(children: Int)
  case class Result(result: String)
}

class ChildrenActor() extends Actor {
  import ChildrenActor._
  import DistributeRevereStringActor.Sorted

  override def receive: Receive = {
    case Reverse(str, id) => sender ! Sorted(str.reverse, id)
  }
}

object ChildrenActor {
  case class Reverse(str: String, id: Int)
}
