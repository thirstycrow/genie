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

    the[ConfigNotFound] thrownBy repo.sync.get(path)

    repo.sync.set(path, value.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config[String](path, value, 1)
  }

  it should "not set a config when the version number does not match" in {
    val path = Path.next
    val value1 = Path.next

    repo.sync.set(path, value1.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config[String](path, value1, 1)

    val value2 = Path.next
    the[ConfigUpdated] thrownBy repo.sync.set(path, value2.getBytes, 2)
  }

  it should "increment the version number when sets a config" in {
    val path = Path.next
    val value1 = Path.next

    repo.sync.set(path, value1.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config[String](path, value1, 1)

    val value2 = Path.next
    repo.sync.set(path, value2.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config[String](path, value2, 2)

    val value3 = Path.next
    repo.sync.set(path, value3.getBytes, 2)
    repo.sync.get(path).map(new String(_)) shouldBe Config[String](path, value3, 3)
  }

  object Path {
    private val i = new AtomicInteger(0)
    def next = "path_" + i.getAndIncrement
  }
}

class SimpleConfigRepoSpec extends ConfigRepoSpec(new SimpleConfigRepo)
