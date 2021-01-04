import Dependencies._
import sbt.Keys.libraryDependencies

ThisBuild / scalaVersion := "2.13.4"
ThisBuild / organization := "net.rollercoders.akka"
ThisBuild / organizationName := "Rollercoders"
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/lucataglia/akka-awp"), "scm:git@github.com:lucataglia/akka-awp"))
ThisBuild / developers := List(
  Developer("lucataglia", "Luca Tagliabue", "tagliabue.luca2@gmail.com", url("https://github.com/lucataglia"))
)

ThisBuild / description := "Akka-awp is a test library that through ActorWithProbe instances helps in writing integration tests for Akka actors applications."
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / homepage := Some(url("https://github.com/lucataglia/akka-awp"))

ThisBuild / publishMavenStyle := true

ThisBuild / crossPaths := true
ThisBuild / pomIncludeRepository := { _ =>
  false
}
ThisBuild / publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "1.0" / ".credentials")
ThisBuild / resolvers += Resolver.jcenterRepo

lazy val root = project
  .in(file("."))
  .settings(
    name := "akka-awp",
    skip in publish := true
  )
  .aggregate(`akka-awp-testkit`)

lazy val `akka-awp-testkit` = project
  .in(file("akka-awp-testkit"))
  .settings(
    name := "akka-awp-testkit",
    libraryDependencies ++= akkaAwpTestKitDeps,
    crossScalaVersions ++= Seq("2.12.12", "2.13.4")
  )

lazy val `akka-awp-examples` = project
  .in(file("akka-awp-examples"))
  .settings(
    name := "akka-awp-examples",
    libraryDependencies ++= akkaAwpExamplesDeps,
    skip in publish := true
  )
  .dependsOn(`akka-awp-testkit`)

// TEST: run "sbt testAkkaAwp" to execute all the tests under akka-awp-examples sub-project
lazy val testAkkaAwp = taskKey[Unit]("Run akka-awp-examples tests")

testAkkaAwp := {
  (`akka-awp-examples` / Test / test).value
}

ThisBuild / scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:postfixOps"
)
