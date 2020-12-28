package examples

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.routing.{RoundRobinGroup, RoundRobinPool}
import examples.DistributeRevereStringWithRoundRobinActor.Slaves
import net.rollercoders.akka.awp.`trait`.AWP

class DistributeRevereStringWithRoundRobinActor(slaves: Slaves) extends Actor with ActorLogging with Stash with AWP {
  import DistributeRevereStringWithRoundRobinActor._
  import SlaveActor.Reverse

  override def preStart(): Unit = {
    slaves match {
      case Pool(pool) =>
        val router = context.actorOf(RoundRobinPool(pool).props(Props[SlaveActor]))
        awpSelf ! Init(router, pool)

      case Group(paths) =>
        val router = context.actorOf(RoundRobinGroup(paths).props())
        awpSelf ! Init(router, paths.length)
    }

  }

  override def receive: Receive = waiting()

  def waiting(): Receive = {
    case Init(router, slaveCount) =>
      log.info(s"[Init] poolSize: $slaveCount")

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

object DistributeRevereStringWithRoundRobinActor {
  sealed trait Slaves
  case class Pool(pool: Int) extends Slaves
  case class Group(paths: List[String]) extends Slaves

  case class Init(router: ActorRef, slaveCount: Int)
  case class Exec(str: String, part: Int)
  case class Sorted(sorted: String, id: Int)
  case class Start(children: Int)
  case class Result(result: String)
}

class SlaveActor() extends Actor {
  import DistributeRevereStringWithRoundRobinActor.Sorted
  import SlaveActor._

  override def receive: Receive = {
    case Reverse(str, id) => sender ! Sorted(str.reverse, id)
  }
}

object SlaveActor {
  case class Reverse(str: String, id: Int)
}
