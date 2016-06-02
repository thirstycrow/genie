import sbt._

object lib {

  object V {
    val curator = "2.10.0"
    val finagle = "6.34.0"
    val logback = "1.1.3"
    val scalaTest = "2.2.4"
    val slf4j = "1.7.12"
    val util = "6.33.0"
  }

  object curator {
    val test = "org.apache.curator" % "curator-test" % V.curator
  }

  object finagle {
    val core = "com.twitter" %% "finagle-core" % V.finagle
  }

  object logback {
    val classic = "ch.qos.logback" % "logback-classic" % V.logback
  }

  val scalaTest = "org.scalatest" %% "scalatest" % V.scalaTest

  object slf4j {
    val api = "org.slf4j" % "slf4j-api" % V.slf4j
    val log4j12 = "org.slf4j" % "slf4j-log4j12" % V.slf4j
    val log4jOverSlf4j = "org.slf4j" % "log4j-over-slf4j" % V.slf4j
  }

  object util {
    val zk = "com.twitter" %% "util-zk" % V.util
  }
}
