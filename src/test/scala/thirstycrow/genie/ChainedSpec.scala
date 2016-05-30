package thirstycrow.genie

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class ChainedSpec extends FlatSpec with Matchers {

  case class TestConfig(
    host: Option[String],
    port: Option[Int],
    key: Option[String],
    value: Option[String])

  it should "chain multiple configs" in {
    val chained = Chained(Seq(
      TestConfig(Some("127.0.0.1"), Some(1234), None, None),
      TestConfig(Some("localhost"), None, Some("name"), None)))
    chained.required(_.host) shouldBe "localhost"
    chained.required(_.port) shouldBe 1234
    chained.required(_.key) shouldBe "name"
    intercept[IllegalStateException](chained.required(_.value))
    chained.optional(_.value) shouldBe None
  }
}
