import sbt._

object lib {

  object V {
    val finagle = "6.34.0"
    val scalaTest = "2.2.4"
  }

  object finagle {
    val core = "com.twitter" %% "finagle-core" % V.finagle
  }

  val scalaTest = "org.scalatest" %% "scalatest" % V.scalaTest
}
