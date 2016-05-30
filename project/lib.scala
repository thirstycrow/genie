import sbt._

object lib {

  object V {
    val curator = "2.10.0"
    val finagle = "6.34.0"
    val jackson = "2.7.2"
    val scalaTest = "2.2.4"
    val util = "6.33.0"
  }

  object curator {
    val test = "org.apache.curator" % "curator-test" % V.curator
  }

  object finagle {
    val core = "com.twitter" %% "finagle-core" % V.finagle
  }

  object jackson {
    object core {
      val databind = "com.fasterxml.jackson.core" % "jackson-databind" % V.jackson
    }
    object module {
      val scala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % V.jackson
    }
  }

  val scalaTest = "org.scalatest" %% "scalatest" % V.scalaTest

  object util {
    val zk = "com.twitter" %% "util-zk" % V.util
  }
}
