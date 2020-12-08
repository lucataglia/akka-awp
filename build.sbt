import Dependencies._

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "org.rollercoders"
ThisBuild / organizationName := "Rollercoders"

name := "ActorWithProbe"

resolvers += Resolver.jcenterRepo
lazy val root = (project in file("."))
  .settings(
    name := "scala-pankov-server",
    libraryDependencies ++= actorWithProbeDeps
  )

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:postfixOps"
)
