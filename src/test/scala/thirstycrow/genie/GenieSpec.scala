package thirstycrow.genie

import com.twitter.conversions.time._
import com.twitter.util.{Await, Future}
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.{FlatSpec, Matchers}
import com.twitter.concurrent.Broker

class GenieSpec extends FlatSpec with Matchers with ZkConfigRepoSupport {

  implicit val timeout = 1 second

  implicit object FooRecipe extends Recipe[Foo] {

    type Cfg = String

    val m = manifest[Cfg]

    def convert(cfg: Chained[String]): Foo = {
      Foo(cfg.required(Option(_)))
    }

    def close(target: Foo) = {
      target.closed = true
    }
  }

  it should "monitor closeable correctly" in {
    val path = nextPath
    val value = nextValue

    val broker = new Broker[Foo]()
    genie.changes[Foo](path).respond(broker ! _)

    genie.repo.rich.sync.set(path, value)
    val foo = Await.result(broker.recv.sync, timeout)
    foo.closed shouldBe false

    val newValue = nextValue
    genie.repo.rich.sync.set(path, newValue)
    val newFoo = Await.result(broker.recv.sync, timeout)
    newFoo.closed shouldBe false
    foo.closed shouldBe true
  }

  private val i = new AtomicInteger(0)
  def nextPath = "path_" + i.getAndIncrement
  def nextValue = "value_" + i.getAndIncrement

  case class Foo(text: String, id: Int = i.getAndIncrement) {
    var closed = false
  }
}
