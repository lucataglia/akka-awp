package actorWithProbe

import actorWithProbe.DistributeRevereStringActor.{Result, Start}
import actorWithProbe.testkit.ActorWithProbe
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{MustMatchers, WordSpecLike}

class DistributeRevereStringActorSpec
    extends TestKit(ActorSystem("awp"))
    with ImplicitSender
    with WordSpecLike
    with MustMatchers {
  private val longString =
    "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum."
  private val expected =
    ".muspI meroL fo snoisrev gnidulcni rekaMegaP sudlA ekil erawtfos gnihsilbup potksed htiw yltnecer erom dna ,segassap muspI meroL gniniatnoc steehs tesarteL fo esaeler eht htiw s0691 eht ni desiralupop saw tI .degnahcnu yllaitnesse gniniamer ,gnittesepyt cinortcele otni pael eht osla tub ,seirutnec evif ylno ton devivrus sah tI .koob nemiceps epyt a ekam ot ti delbmarcs dna epyt fo yellag a koot retnirp nwonknu na nehw ,s0051 eht ecnis reve txet ymmud dradnats s'yrtsudni eht neeb sah muspI meroL .yrtsudni gnittesepyt dna gnitnirp eht fo txet ymmud ylpmis si muspI meroL"

  private val notSoLongString = "We are going to test the algorithm using the ActorWithProbe library"

  "Distribute Reverse String" must {
    "answer with the reversed string" in {
      val children = 12
      val distributedSorter =
        ActorWithProbe
          .actorOf(ref =>
                     Props(new DistributeRevereStringActor(children, longString) {
                       override implicit val awpSelf: ActorRef = ref
                     }),
                   "distributedSorter-1",
                   verbose = true)

      distributedSorter eventuallyReceiveMsg Start(children)
      distributedSorter eventuallyReceiveMsg Result(expected)
    }

    "answer with a Result message when the algorithm ends" in {
      val children = 4
      val distributedSorter =
        ActorWithProbe
          .actorOf(ref =>
                     Props(new DistributeRevereStringActor(children, notSoLongString) {
                       override implicit val awpSelf: ActorRef = ref
                     }),
                   "distributedSorter-2",
                   verbose = true)

      distributedSorter.eventuallyReceiveMsgType[Start]
      distributedSorter.eventuallyReceiveMsgType[Result]
    }
  }
}
