import sbt._

object lib {

  object V {
    val curator = "2.10.0"
    val embeddedRedis = "0.6"
    val finagle = "6.34.0"
    val jackson = "2.7.2"
    val logback = "1.1.3"
    val mysqlConnectorMxj = "5.0.12"
    val scalaTest = "2.2.4"
    val slf4j = "1.7.12"
    val util = "6.33.0"
  }

  object curator {
    val test = "org.apache.curator" % "curator-test" % V.curator
  }

  val embeddedRedis = "com.github.kstyrc" % "embedded-redis" % V.embeddedRedis

  object finagle {
    val core = "com.twitter" %% "finagle-core" % V.finagle
    val mysql = "com.twitter" %% "finagle-mysql" % V.finagle
  }

  object jackson {
    object core {
      val databind = "com.fasterxml.jackson.core" % "jackson-databind" % V.jackson
    }
    object module {
      val scala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % V.jackson
    }
  }

  object logback {
    val classic = "ch.qos.logback" % "logback-classic" % V.logback
  }

  val mysqlConnectorMxj = "mysql" % "mysql-connector-mxj" % V.mysqlConnectorMxj

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
