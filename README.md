# Actor With Probe 
A lightweight testing library that works along with [akka-testkit][akka-testkit]
to makes integration tests on complex actors network easier.

## Main goal
Akka-awp can help in testing complex and less complex systems made by Akka actors 
Create an actor's instance using akka-awp API makes available invoking
akka-testkit method on it like it is a TestProbe. This makes available
to test write small tests that check complex actor networks for regression,
just making sure that given the initial message, the wanted output is
the one expected.     

![](img/akka-awp-diagram.svg "akka-awp-dia")

In a scenario like the one above, the testing line will be:
```
actor eventuallyReceiveMsg Output
```
 
## Examples

[akka-testkit]:https://doc.akka.io/docs/akka/current/testing.html