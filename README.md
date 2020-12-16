# Actor With Probe 
A lightweight testing library that works along with [akka-testkit][akka-testkit]
to make integration tests on complex actors network easier.

## Main goal
Akka-awp can help in testing complex and less complex applications made by Akka actors. 
Create an actor using akka-awp API makes invoking new test methods available on the
ActorRef other than all the akka-testkit methods like is it a TestProbe. 

The goal is to help in writing smaller tests that check complex system 
for correctness and regression. The test just need to make sure that given 
the initial message the wanted output is the one expected.      

![](img/akka-awp-diagram.svg "akka-awp-dia")

In a scenario like the one above, the testing line will be:
```
actor eventuallyReceiveMsg Output
```
 
## Examples
To see more test examples please take a look at the [test folder][akka-awp-tests].

### Distributed reverse string algorithm
Given a long string the actors app will return the string reverted. Our system is
organized as follow:
* A master actor when start spawn N slaves. The number is set at creation time.
* When the master receive the Exec message, he get the string to reverse and the
number of slaves he had to generate.
* Using a [Round Robin][akka-round-robin] algorithm the master send to each slave a slice of the whole
string. This value is the one the slave has to manage. 

![](img/akka-awp-diagram-example.svg "akka-awp-dia")

* Each slave answer the master.
* The master merge together all the slaves responses sending to itself the final
result through a Result message.

![](img/akka-awp-diagram-example-part2.svg "akka-awp-dia")

Given that behavior, testing this application can be done as following:
```
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
```
To see the source code of this test please take a look at the [test folder][akka-awp-tests].

## Diving down in technical details

### How is possible invoke test method on real actor
Akka-awp is able to invoke test methods on real actors because it handles
under the hood a hidden TestProbe that receives all the messages the
actor get.

### How is possible test responses (e.g. sender() ! Msg) 
When an actor send a message the the `!` method, there is an `implicit val varName: ActorRef = ???` 
that is taken as sender, usually the self val inherited from the Actor trait.
This value need to be redefined to let akka-awp works properly. Each actor
must also extend the *AWP trait* that force to explicitly define
an *implicit val* named *awpSelf* (here some [examples][akka-awp-test-actors]). 
That the value must be set as following: `implicit val awpSelf: ActorRef = self`. 
This will makes the awpSelf ActorRef as implicit sender.
 
The implicit has to be redefined into the user defined actor code in order
to avoid conflict in implicit resolution. 

### How is possible test auto messages (e.g. self ! Msg)
Is not possible. Using `self` directly prevents akka-awp to intercept the
messages. If the needs is to test also the auto-messages, the ActorRef used must me awpSelf:
e.g. `awpSelf ! Msg`. 

*Hint* The auto-messages, like private methods in Object-oriented programming, 
should not be tested.

### Corner cases
In this section I'll list all the corner case I found.
* Also using awpSelf as explained before do not makes available to intectept the messages that 
come from [timers][akka-timers] (e.g. `timers.startTimerAtFixedRate(Key, Msg, 1 second)`. These
messages are sent using the self that comes from the Actor trait.

[akka-testkit]:https://doc.akka.io/docs/akka/current/testing.html
[akka-awp-tests]: https://github.com/lucataglia/akka-awp/tree/main/src/test/scala/actorWithProbe
[akka-round-robin]: https://doc.akka.io/docs/akka/current/routing.html
[akka-timers]: https://doc.akka.io/api/akka/current/akka/actor/Timers.html
[akka-awp-test-actors]: https://github.com/lucataglia/akka-awp/blob/main/src/main/scala/actorWithProbe/TestActors.scala