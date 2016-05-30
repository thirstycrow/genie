organization := "thirstycrow"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.5")

lazy val genie = project
  .in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      lib.finagle.core,
      lib.jackson.core.databind,
      lib.jackson.module.scala,
      lib.util.zk,
      lib.curator.test % "test",
      lib.scalaTest % "test"
    )
  )
