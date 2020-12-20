package actorWithProbe

import actorWithProbe.DistributeRevereStringWithRoundRobinActor.{Exec, Group, Pool, Result}
import actorWithProbe.SlaveActor.Reverse
import actorWithProbe.testkit.ActorWithProbe
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

class DistributeRevereStringActorSpec
    extends TestKit(ActorSystem("awp"))
    with ImplicitSender
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  private val longString =
    "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum."
  private val expected =
    ".muspI meroL fo snoisrev gnidulcni rekaMegaP sudlA ekil erawtfos gnihsilbup potksed htiw yltnecer erom dna ,segassap muspI meroL gniniatnoc steehs tesarteL fo esaeler eht htiw s0691 eht ni desiralupop saw tI .degnahcnu yllaitnesse gniniamer ,gnittesepyt cinortcele otni pael eht osla tub ,seirutnec evif ylno ton devivrus sah tI .koob nemiceps epyt a ekam ot ti delbmarcs dna epyt fo yellag a koot retnirp nwonknu na nehw ,s0051 eht ecnis reve txet ymmud dradnats s'yrtsudni eht neeb sah muspI meroL .yrtsudni gnittesepyt dna gnitnirp eht fo txet ymmud ylpmis si muspI meroL"

  private val notSoLongString = "We are going to test the algorithm using the ActorWithProbe library"

  "Distribute Reverse String using routing pool" must {
    "answer with the reversed string" in {

      val distributedSorter =
        ActorWithProbe
          .actorOf(ref =>
                     Props(new DistributeRevereStringWithRoundRobinActor(Pool(12)) {
                       override implicit val awpSelf: ActorRef = ref
                     }),
                   "distributedSorter-1",
                   verbose = true)

      distributedSorter ! Exec(longString, 10)
      distributedSorter eventuallyReceiveMsg Exec(longString, 10)
      distributedSorter eventuallyReceiveMsg Result(expected)
    }

    "answer with a Result message when the algorithm ends" in {
      val distributedSorter =
        ActorWithProbe
          .actorOf(ref =>
                     Props(new DistributeRevereStringWithRoundRobinActor(Pool(8)) {
                       override implicit val awpSelf: ActorRef = ref
                     }),
                   "distributedSorter-2",
                   verbose = true)

      distributedSorter ! Exec(notSoLongString, 2)
      distributedSorter.eventuallyReceiveMsgType[Exec]
      distributedSorter.eventuallyReceiveMsgType[Result]
    }
  }

  "Distribute Reverse String using routing group" must {
    "answer with the reversed string" in {
      val slaves = (0 until 10)
        .map(i => ActorWithProbe.actorOf(Props[SlaveActor], s"slave_$i", true))

      val slavesPath = slaves.map(_.path.toString).toList

      val distributedSorter =
        ActorWithProbe.actorOf(
          ref =>
            Props(new DistributeRevereStringWithRoundRobinActor(Group(slavesPath)) {
              override implicit val awpSelf: ActorRef = ref
            }),
          "distributedSorter-3",
          verbose = true
        )

      distributedSorter ! Exec(longString, 10)

      slaves.foreach(slave => slave.eventuallyReceiveMsgType[Reverse])
      distributedSorter eventuallyReceiveMsg Exec(longString, 10)
      distributedSorter eventuallyReceiveMsg Result(expected)
    }
  }
}
