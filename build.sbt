organization := "thirstycrow"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.5")

lazy val genie = project
  .in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      lib.finagle.core,
      lib.slf4j.api,
      lib.slf4j.log4jOverSlf4j,
      lib.util.zk
    ).map { dep =>
      dep.exclude("log4j", "log4j")
       .exclude("org.slf4j", "slf4j-log4j12")
    },
    libraryDependencies ++= Seq(
      lib.curator.test % "test",
      lib.scalaTest % "test",
      lib.logback.classic % "test"
    ),
    fork in Test := true
  )
