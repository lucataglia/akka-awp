# Actor With Probe 
A lightweight testing library that works along with [akka-testkit][akka-testkit]
to make integration tests on complex actors network easier.

## Table of contents
- [Main goal](#main-goal)
- [Examples](#examples)
    * [Distributed reverse string (high-level API)](#distributed-reverse-string)
    * [Silly Actor (low-level API)](#silly-actor)
- [Getting started](#getting-started)
- [FAQ](#faq)

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
The aim of this section is to give a quick overview about how it looks an akka-awp test.
The first example has been written using low-level API while the second one has been written
using the high-level API. Both the examples could be re-written using the other approach. 

To see more test examples please take a look at the [test folder][akka-awp-tests].

### Distributed reverse string 
#### (low-level API)
This example will show you how akka-awp allow testing a complex system.

Given a long string the algorithm returns the string reverted. The actors are
organized as follow:
* A master actor spawn N slaves. The number of slaves is set at creation time.
* The master receive the the string to reverse through an Exec message along with
the number of slaves he had to generate.
* Using a [Round Robin][akka-round-robin] algorithm the master send to each slave a slice of the whole
string. This value is the one the slave has to manage. 

![](img/akka-awp-diagram-example.svg "akka-awp-distributed-dia")

* Each slave answer the master.
* The master merge together all the slaves responses sending to itself the final
result through a Result message.

![](img/akka-awp-diagram-example-part2.svg "akka-awp-distributed-part-2-dia")

Given that behavior, testing this application can be done as following:
```
"Distribute Reverse String using routing pool" must {
    "answer with the reversed string" in {

      val distributedSorter =
        ActorWithProbe
          .actorOf(ref =>
                     Props(new DistributeRevereStringWithRoundRobinActor(Pool(12)) {
                       override implicit val awpSelf: ActorRef = ref
                     }))

      distributedSorter ! Exec(longString, 10)
      distributedSorter eventuallyReceiveMsg Exec(longString, 10)
      distributedSorter eventuallyReceiveMsg Result(expected)
    }
```
To see the source code of this test please take a look at the [test folder][akka-awp-tests].

### Silly Actor 
#### (high-level API)
This example will show you how the high level API of akka-awp allow testing also
the mailbox of the original sender.


![](img/akka-awp-diagram-silly-actor.svg "akka-awp-silly-actor-dia")

Let's describe step by step the flow of this execution:
1. The testActor from the ImplicitSender trait send a String message to the SillyActor.
2. The SillyActor send to itself the received String message wrapped into an Envelop case class
3. The SillyActor, once received the Envelop, answer the original sender (aka the testActor) with an
pre-defined answer

```
"An actor that send to itself a message" must {
    "be able to test if he received it" in {
     
      val sillyRef =
        ActorWithProbe.actorOf(
          ref =>
            Props(new SillyActor("Got the message") {
              override implicit val awpSelf: ActorRef = ref
            })
        )

      sillyRef ! "Hello akka-awp" thenWaitFor sillyRef receiving Envelop("Hello akka-awp") andThenWaitMeReceiving "Got the message"
    }
  }
```

To see the source code of this test please take a look at the [test folder][akka-awp-tests].

## Getting Started
### Create an ActorWithProbe instance

```
def actorOf(f: ActorRef => Props, name: String, verbose: Boolean = true)(implicit system: ActorSystem): ActorWithProbe
def actorOf(props: Props, name: String, verbose: Boolean = true)(implicit system: ActorSystem): ActorWithProbe
def actorOf(actorRef: ActorRef, name: String, verbose: Boolean = true)(implicit system: ActorSystem): ActorWithProbe
```
Source code [here][testkit-pakage]

These are the available static methods that can be used to create an ActorWithProbe instance.
An awp instance is meant to receive all the messages the actor he wrap receive so its
behavior can be tested. The first approach is the only one that makes available testing
the responses the wrapped actor receive (e.g. sender() ! Response). Every actor we want
to test for the "responses" must extend the AWP trait:
```
trait AWP {
  this: Actor =>
  implicit val awpSelf: ActorRef
}
``` 
This trait force to explicitly define an `implicit val actorRef: ActorRef` into the 
user-defined actor, and that definition can than be overwritten into the test as 
follow:

```
val sillyRef =
  ActorWithProbe.actorOf(
    ref =>
      Props(new SillyActor("Got the message") {
        override implicit val awpSelf: ActorRef = ref
      }),
    verbose = true
)
```
This anonymous class syntax allow to overwrite the `implicit ActorRef` set by the AWP
trait and so manage which is the actorRef used by the`!` method as implicit sender. The
value that has to be used is `ref` i.e. the awp reference exposed by the library.
In that way we are sure that every message send to the sillyActor will be intercept
first by the awp.

### Testing with awp
 
Akka-awp makes available all the methods from the [akka-testkit][akka-testkit] library
but also expose some new methods. Those methods are utility methods build on top of
akka-testkit and allow to easily test if an actor will receive a message before
a timeout expires. This utility was written with the goal to expose a declarative
programming approach.
```
def thenWaitMeReceiving
def thenWaitMeReceivingType[T]

def thenWaitFor -> def receiving
def thenWaitForAll -> def receivingType[T]
``` 
Let's use the SillyActor example to show how this methods can be used. Go back to take
a look at the [diagram][silly-actor-example] if the SillyActor behavior is not clear.

**Checking if the SillyActor got the Envelop**
```
sillyRef ! "Hello akka-awp" thenWaitFor sillyRef receiving Envelop("Hello akka-awp")
```

**Checking if the SillyActor got a message of type Envelop**
```
sillyRef.!("Hello akka-awp").thenWaitFor(sillyRef).receivingType[Envelop]
```

**Checking if the testActor got the answer**
```
sillyRef ! "Hello akka-awp" thenWaitMeReceiving "Got the message"
```

**Checking if the testActor got a message of type String**
```
sillyRef.!("Hello akka-awp").andThenWaitMeReceivingType[String]
```

**Checking if the SillyActor got the Envelop and if the testActor got the answer**
```
sillyRef ! "Hello akka-awp" thenWaitFor sillyRef receiving Envelop("Hello akka-awp") andThenWaitMeReceiving "Got the message"
```

**Checking if the SillyActor got a message of type Envelop and if the testActor got a message of type String**
```
sillyRef.!("Hello akka-awp").thenWaitFor(sillyRef).receivingType[Envelop].andThenWaitMeReceivingType[String]
```

**Checking if the SillyActor got the Envelop with akka-testkit**
```
sillyRef ! "Hello akka-awp"
sillyRef expectMsg "Hello akka-awp"
sillyRef expectMsg Envelop("Hello akka-awp")
```

**Checking if the SillyActor got the Envelop with akka-awp low-level API**
```
sillyRef ! "Hello akka-awp"
sillyRef eventuallyReceiveMsg Envelop("Hello akka-awp")
```


## FAQ

### How is possible invoke test method on real actor ?
Akka-awp is able to invoke test methods on real actors because it handles
under the hood a hidden TestProbe that receives all the messages the
actor get.

### How is possible test responses (e.g. sender() ! Msg) ? 
When an actor send a message the the `!` method, there is an `implicit val varName: ActorRef = ???` 
that is taken as sender, usually the self val inherited from the Actor trait.
This value need to be redefined to let akka-awp works properly. Each actor
must also extend the *AWP trait* that force to explicitly define
an *implicit val* named *awpSelf* (here some [examples][akka-awp-test-actors]). 
That the value must be set as following: `implicit val awpSelf: ActorRef = self`. 
This will makes the awpSelf ActorRef as implicit sender.
 
The implicit has to be redefined into the user defined actor code in order
to avoid conflict in implicit resolution. 

### How is possible test auto messages (e.g. self ! Msg) ?
It isn't. Using `self` directly prevents akka-awp to intercept the
messages. If the needs is to test also the auto-messages, the ActorRef used must me awpSelf:
e.g. `awpSelf ! Msg`. 

*Hint* The auto-messages, like private methods in Object-oriented programming, 
should not be tested.

### Corner cases
In this section I'll list all the corner case I found.
* Also using awpSelf as explained before do not makes available to intectept the messages that 
come from [timers][akka-timers] (e.g. `timers.startTimerAtFixedRate(Key, Msg, 1 second)`. These
messages are sent using the `self` ActorRef that comes from the Actor trait.


Akka-awp redefine the `!` method to makes available these test API.
* **thanWaitFor**: tells which actor we are going to test next
* **andThenWaitFor**: the same as thanWaitFor.
* **thenWaitMeReceiving**: TODO) 
 

[akka-testkit]:https://doc.akka.io/docs/akka/current/testing.html
[akka-awp-tests]: https://github.com/lucataglia/akka-awp/tree/main/src/test/scala/actorWithProbe
[akka-round-robin]: https://doc.akka.io/docs/akka/current/routing.html
[akka-timers]: https://doc.akka.io/api/akka/current/akka/actor/Timers.html
[akka-awp-test-actors]: https://github.com/lucataglia/akka-awp/blob/main/src/main/scala/actorWithProbe/TestActors.scala
[testkit-pakage]: https://github.com/lucataglia/akka-awp/blob/main/src/main/scala/actorWithProbe/testkit/package.scala
[silly-actor-example]: https://github.com/lucataglia/akka-awp#silly-actor