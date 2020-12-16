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
To see more examples please take a look at the [test folder][akka-awp-tests].

### Distributed reverse string algorithm
Given a long string the actors app will return the string reverted. Our system is
organized as follow:
* A master actor when start spawn N slaves. The number is set at creation time.
* When the master receive the Exec message, he get the string to reverse and the
number of slaves he had to generate.
* Using a Round Robin algorithm the master send to each slave a slice of the whole
string. This value is the one the slave has to manage. 

![](img/akka-awp-diagram-example.svg "akka-awp-dia")

* The slave answer the master.
* The master merge together all the slaves responses sending to itself the final
result through a Result message.

![](img/akka-awp-diagram-example-part2.svg "akka-awp-dia")

Given that behavior, testing this application can be done as follow
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

##Diving down in technical details

[akka-testkit]:https://doc.akka.io/docs/akka/current/testing.html
[akka-awp-tests]: https://github.com/lucataglia/akka-awp/tree/main/src/test/scala/actorWithProbe