import Dependencies._

scalaVersion := "2.12.8"

organization := "net.rollercoders.akka"
organizationName := "Rollercoders"
homepage := Some(url("https://github.com/lucataglia/akka-awp"))
scmInfo := Some(ScmInfo(url("https://github.com/lucataglia/akka-awp"), "git@github.com:lucataglia/akka-awp"))
developers := List(
  Developer("lucataglia", "Luca Tagliabue", "tagliabue.luca2@gmail.com", url("https://github.com/lucataglia"))
)
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
publishMavenStyle := true

crossPaths := false

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

credentials += Credentials(Path.userHome / ".sbt" / "1.0" / ".credentials")

resolvers += Resolver.jcenterRepo
lazy val root = (project in file("."))
  .settings(
    name := "akka-awp",
    libraryDependencies ++= actorWithProbeDeps
  )

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:postfixOps"
)
