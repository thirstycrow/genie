organization := "thirstycrow"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.5")

lazy val genie = project
  .in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      lib.finagle.core,
      lib.scalaTest % "test"
    )
  )
