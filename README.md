# Akka-awp 
A lightweight testing library which goal is to make integration tests on complex actors application easier.

## Table of contents
- [Abstract](#abstract)
- [Examples](#examples)
    * [Distributed reverse string (high-level API)](#distributed-reverse-string)
    * [Silly Actor (low-level API)](#silly-actor)
- [Getting started](#getting-started)
    * [Create an ActorWithProbe instance](#create-an-actorwithprobe-instance)
    * [Testing an ActorWithProbe mailbox](#testing-an-actorwithprobe-mailbox)
- [FAQ](#faq)

## Abstract
Akka-awp goal is to help in writing smaller tests that check complex actor application 
for correctness and regression. The core idea was to write a library that can 
answer the following question: 
*How can I test my system just checking the final result (aka message) of the computation ?*     

![](img/akka-awp-diagram.svg "akka-awp-dia")

In a scenario like this, the testing line will be:

<pre>
awp ! DoSomething <b>thenWaitFor</b> awp <b>receiving</b> Output
</pre>

In other words here we are saying:

```
Send to awp the DoSomething message, let all the actors execute their logic and then check that awp got the Output message
```

Here the tested awp is also the one that got the first message, but this is just the 
simplest use case, not the only scenario covered.  
 
## Examples
The aim of this section is to give a quick overview about how it looks like an akka-awp test.
The first example has been written using low-level API while the second one has been written
using the high-level API. Both the examples could be re-written using the other approach. 

To see more test examples please take a look at the [test folder][akka-awp-tests].

### Distributed reverse string 
#### (low-level API)
This example will show you how akka-awp allow testing a complex system. 
Check [here][akka-awp-distributed-reverse-actor] the source code of the actor.

Given a long string the algorithm returns the string reverted as follows:
* A master actor spawn N slaves. The number of slaves is set at creation time.
* The master receive the string to reverse and the parallelism to use for that string
through an Exec message (for a string we can use just 2 Slave, for another longer string maybe
10).
* Using a [Round Robin][akka-round-robin] algorithm the master send to each slave a slice of the whole
string. This value is the one the slave has to manage. 

![](img/akka-awp-diagram-example.svg "akka-awp-distributed-dia")

* Each slave answer the master.
* The master merge together all the slaves responses sending to itself the final
result through a Result message.

![](img/akka-awp-diagram-example-part2.svg "akka-awp-distributed-part-2-dia")

Given that behavior, testing this application can be done as follows:

```scala
"Distribute Reverse String using routing pool" must {
    "answer with the reversed string" in {

      val distributedSorter =
        ActorWithProbe
          .actorOf(ref =>
             Props(new DistributeRevereStringWithRoundRobinActor(Pool(12)) {
               override implicit val awpSelf: ActorRef = ref
             }))
          .withName("sorter-1")
          .build()

      distributedSorter ! Exec(longString, 10)
      distributedSorter eventuallyReceiveMsg Exec(longString, 10)
      distributedSorter eventuallyReceiveMsg Result(expected)
    }
```
To see the source code of this test please take a look at the [test folder][akka-awp-tests].

### Silly Actor 
#### (high-level API)
This example will show you how the high-level API of akka-awp allow testing also
the mailbox of the original sender. The high-level API are built on top of the low-level 
API. Check [here][akka-awp-silly-actor] the source code of the actor.


![](img/akka-awp-diagram-silly-actor.svg "akka-awp-silly-actor-dia")

The behavior is the following:
1. The testActor from the ImplicitSender trait send a String message to the SillyActor.
2. The SillyActor send to itself the received String message wrapped into an Envelop case class
3. The SillyActor, once received the Envelop, answer the original sender (aka the testActor) with a
pre-defined answer

```scala
"An actor that send to itself a message" must {
    "be able to test if he received it" in {
     
      val sillyRef =
        ActorWithProbe
          .actorOf(
            ref =>
              Props(new SillyActor("Got the message") {
                override implicit val awpSelf: ActorRef = ref
              })
          )
          .withName("silly")
          .build()

      sillyRef ! "Hello akka-awp" thenWaitFor sillyRef receiving Envelop("Hello akka-awp") andThenWaitMeReceiving "Got the message"
    }
  }
```

To see the source code of this test please take a look at the [test folder][akka-awp-tests].

## Getting Started
Akka-awp API exposes some factory methods that makes available create an **ActorWithProbe**
instance i.e. an Akka actor on which can be invoked every method of [akka-testkit][akka-testkit] 
(like is it a TestProbe) other than new test methods implemented by the library.

In order to have a concrete example to reference, here below will be used the [SillyActor][silly-actor-example] example.

### Create an ActorWithProbe instance

```scala
def actorOf(f: ActorRef => Props)(implicit system: ActorSystem): ActorWithProbeBuilder
def actorOf(props: Props)(implicit system: ActorSystem): ActorWithProbeBuilder
def actorOf(actorRef: ActorRef)(implicit system: ActorSystem): ActorWithProbeBuilder
```
Source code [here][testkit-pakage]

These are the available static methods that can be used to create an ActorWithProbe.
An awp instance will wrap your actor and will receive all the messages he received.
Have those messages into the awp actor mailbox is mandatory to let the test methods work.
The first method is the one that will be use more often since is the only one that makes available testing
the responses the wrapped actor receive (e.g. sender() ! Response). Every user-defined 
actor we want to test for the "responses" must extend the AWP trait:

```scala
trait AWP {
  this: Actor =>
  implicit val awpSelf: ActorRef
}
``` 

AWP trait force to explicitly define an `implicit val actorRef: ActorRef` into the 
user-defined actor. Often the definition goes `implicit val awpSelf: ActorRef = self`,
since there is no need to change the usual behavior of the self ActorRef. That trait is 
needed to avoid implicit resolution conflict.
Done that, the `implicit val awpSelf` can be overwritten into test code as follows:

```scala
val sillyRef =
  ActorWithProbe
    .actorOf(
      ref =>
        Props(new SillyActor("Got the message") {
          override implicit val awpSelf: ActorRef = ref
        })
    )
    .withName("silly")
    .build()
)
```

This anonymous class syntax allow to overwrite the `implicit ActorRef` set by the AWP
trait and so manage which is the implicit actorRef used by the`!` method as implicit sender.
The value used must be `ref` i.e. the awp reference exposed by the library.
In that way we are sure every message send to the sillyActor will be managed
first by its awp (we are populating its mailbox).

### Testing an ActorWithProbe mailbox
 
Akka-awp makes available all the methods from the [akka-testkit][akka-testkit] library
but also expose some new methods to test actors. Those methods are built on top of
akka-testkit and allow to easily test if an actor will receive a message before
a timeout expires. One of the goal of this high-level API is to expose a declarative
programming approach and that would be possible because the library also redefined the `!` 
method.

```scala
// To test if the original sender receive some message
def thenWaitMeReceiving
def thenWaitMeReceivingType[T]

// To test if a specific awp receive some message
def thenWaitFor 
def thenWaitForAll
def receiving
def receivingType[T]
``` 

Let's use the SillyActor example to show how the syntax works. Go back to take
a look at the [diagram][silly-actor-example] or check [here][akka-awp-silly-actor] the source code
of the SillyActor behavior if something is not clear.
        
<div><pre>
<b>[1]</b>
<b></b>
<b>Query</b>  | SillyActor got the Envelop:
<b></b>       |
<b>Syntax</b> | sillyRef ! "Hello akka-awp" <b>thenWaitFor</b> sillyRef <b>receiving</b> Envelop("Hello akka-awp")
</pre></div>
        
<div><pre>
<b>[2]</b>
<b></b>
<b>Query</b>  | SillyActor got a message of type Envelop:
<b></b>       |
<b>Syntax</b> | sillyRef.!("Hello akka-awp").<b>thenWaitFor</b>(sillyRef).<b>receivingType</b>[Envelop]
</pre></div>

<div><pre>
<b>[3]</b>
<b></b>
<b>Query</b>  | testActor got the answer
<b></b>       |
<b>Syntax</b> | sillyRef ! "Hello akka-awp" <b>thenWaitMeReceiving</b> "Got the message"
</pre></div>

<div><pre>
<b>[4]</b>
<b></b>
<b>Query</b>  | testActor got a message of type String:
<b></b>       |
<b>Syntax</b> | sillyRef.!("Hello akka-awp").<b>andThenWaitMeReceivingType</b>[String]
</pre></div>

<div><pre>
<b>[5]</b>
<b></b>
<b>Query</b>  | SillyActor got the Envelop and testActor got the answer:
<b></b>       |
<b>Syntax</b> | sillyRef ! "Hello akka-awp" <b>thenWaitFor</b> sillyRef <b>receiving</b> Envelop("Hello akka-awp") <b>andThenWaitMeReceiving</b> "Got the message"
</pre></div>
         
<div><pre>
<b>[6]</b> 
<b></b> 
<b>Query</b>  | SillyActor got a message of type Envelop and testActor got a message of type String:
<b></b>       |
<b>Syntax</b> | sillyRef.!("Hello akka-awp").<b>thenWaitFor</b>(sillyRef).<b>receivingType</b>[Envelop].<b>andThenWaitMeReceivingType</b>[String]
</pre></div>           
        
**SillyActor got the Envelop (akka-awp low-level API)**

```scala
// [1]

sillyRef ! "Hello akka-awp"
sillyRef eventuallyReceiveMsg Envelop("Hello akka-awp")
```

**SillyActor got the Envelop (akka-testkit)**

```scala
// [1]

sillyRef ! "Hello akka-awp"
sillyRef expectMsg "Hello akka-awp"
sillyRef expectMsg Envelop("Hello akka-awp")
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
  
# License
Akka-awp is Open Source and available under the Apache 2 License.

This code uses [Akka][akka] which is licensed under the Apache 2 License, and can be obtained [here][akka]



[akka]: https://github.com/akka/akka
[akka-testkit]:https://doc.akka.io/docs/akka/current/testing.html
[akka-awp-tests]: https://github.com/lucataglia/akka-awp/tree/main/src/test/scala/actorWithProbe
[akka-awp-silly-actor]: https://github.com/lucataglia/akka-awp/blob/main/src/main/scala/actorWithProbe/TestActors.scala
[akka-awp-distributed-reverse-actor]: https://github.com/lucataglia/akka-awp/blob/main/src/main/scala/actorWithProbe/DistributeRevereStringActor.scala
[akka-round-robin]: https://doc.akka.io/docs/akka/current/routing.html
[akka-timers]: https://doc.akka.io/api/akka/current/akka/actor/Timers.html
[akka-awp-test-actors]: https://github.com/lucataglia/akka-awp/blob/main/src/main/scala/actorWithProbe/TestActors.scala
[testkit-pakage]: https://github.com/lucataglia/akka-awp/blob/main/src/main/scala/actorWithProbe/testkit/package.scala
[silly-actor-example]: https://github.com/lucataglia/akka-awp#silly-actor