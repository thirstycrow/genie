package thirstycrow.genie

import com.twitter.conversions.time._
import com.twitter.util.Future
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.{ FlatSpec, Matchers }

class GenieSpec extends FlatSpec with Matchers with ZkConfigRepoSupport {

  implicit val timeout = 1 second

  implicit object FooRecipe extends Recipe[Foo] with CloseableRecipe[Foo] {

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

    genie.syncRichRepo.set(path, value)
    val fooVar = genie.sync.monitor[Foo](path)
    val foo = fooVar.sample()
    foo.closed shouldBe false

    val newValue = nextValue
    genie.syncRichRepo.set(path, newValue)
    AsyncUtils.keepTrying {
      Future {
        val newFoo = fooVar.sample()
        newFoo.closed shouldBe false
        foo.closed shouldBe true
      }
    }
  }

  private val i = new AtomicInteger(0)
  def nextPath = "path_" + i.getAndIncrement
  def nextValue = "value_" + i.getAndIncrement

  case class Foo(text: String, id: Int = i.getAndIncrement) {
    var closed = false
  }
}
