import sbt._

object Dependencies {
  private object akka {
    val namespace = "com.typesafe.akka"
    val version = "2.6.10"
    val core = namespace %% "akka-actor" % version
    val test = namespace %% "akka-testkit" % version
  }

  private object scalaTest {
    val namespace = "org.scalatest"
    val version = "3.2.2"
    val core = namespace %% "scalatest" % version
  }

  lazy val akkaAwpTestKitDeps =
    Seq(akka.core % Provided, akka.test % Provided)

  lazy val akkaAwpExamplesDeps =
    Seq(akka.core, akka.test, scalaTest.core)
}
