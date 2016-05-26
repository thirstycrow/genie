package thirstycrow.genie

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.twitter.conversions.time.intToTimeableNumber

abstract class ConfigRepoSpec(repo: ConfigRepo) extends FlatSpec with Matchers {

  implicit val futureTimeout = 1 second

  it should "set/get config" in {
    val path = Path.next
    val value = "test config"

    the[ConfigNotFound] thrownBy repo.sync.get(path) shouldBe ConfigNotFound(path)

    repo.sync.set(path, value.getBytes)
    val config = repo.sync.get(path)
    config.path shouldBe path
    new String(config.value) shouldBe value
  }

  object Path {
    private val i = new AtomicInteger(0)
    def next = "path_" + i.getAndIncrement
  }
}

class SimpleConfigRepoSpec extends ConfigRepoSpec(new SimpleConfigRepo)
