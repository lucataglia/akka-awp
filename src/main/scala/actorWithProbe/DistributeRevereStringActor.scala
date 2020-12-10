package actorWithProbe

import actorWithProbe.ChildrenActor.Reverse
import actorWithProbe.testkit.AWP
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}

class DistributeRevereStringActor(children: Int, longString: String)
    extends Actor
    with ActorLogging
    with Stash
    with AWP {
  import DistributeRevereStringActor._

  override def preStart(): Unit = {
    val len = longString.length / children
    (0 until children)
      .foreach(index => {
        val start = len * index
        val end = if (index == children - 1) longString.length else len * (index + 1)

        val subStr = longString.substring(start, end)
        context.actorOf(Props[ChildrenActor]) ! Reverse(subStr, index)
      })

    awpSelf ! Start(children)
  }

  override def receive: Receive = {
    case Start(children) =>
      context.become(waiting(children))
      unstashAll()

    case _ =>
      stash()
  }

  def waiting(left: Int, answers: Vector[(String, Int)] = Vector.empty[(String, Int)]): Receive = {
    case Sorted(sorted, id) if left == 1 => {
      val result = (answers :+ (sorted, id)).sortBy(_._2).reverse.map(_._1).mkString("")
      awpSelf ! Result(result)
    }

    case Sorted(sorted, id) =>
      context.become(waiting(left - 1, answers :+ (sorted, id)))

    case Result(result) =>
      log.info(result)
  }

  override implicit val awpSelf: ActorRef = self
}

object DistributeRevereStringActor {
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
