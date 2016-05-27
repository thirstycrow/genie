package thirstycrow.genie

import java.util.concurrent.atomic.AtomicInteger

import org.apache.curator.test.TestingServer
import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.ZooDefs.Perms
import org.apache.zookeeper.data.ACL
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.finagle.util.DefaultTimer
import com.twitter.zk.ZkClient

abstract class ConfigRepoSpec extends FlatSpec with Matchers {

  implicit val futureTimeout = 1 second

  val repo: ConfigRepo

  it should "cause an error when trying to get a missing config" in {
    val path = nextPath
    the[ConfigNotFound] thrownBy repo.sync.get(path)
  }

  it should "get an existing config" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "cause an error when trying to update a missing config" in {
    val path = nextPath
    val value = nextValue
    the[ConfigUpdated] thrownBy repo.sync.set(path, value.getBytes, 0)
    the[ConfigNotFound] thrownBy repo.sync.get(path)
  }

  it should "cause an error when trying to update an existing config with unmatching version number" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    the[ConfigUpdated] thrownBy repo.sync.set(path, nextValue.getBytes, 1)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "update an existing config with matching version number, and increment the version number" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    val newValue = nextValue
    repo.sync.set(path, newValue.getBytes, 0)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, newValue, 1)
  }

  it should "force update a config even if the config node dose not exist" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "force update a config even if any ancestor node dose not exist" in {
    val path = Seq.fill(3)(nextPath).mkString("/")
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "force update a config without a version number" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    val newValue = nextValue
    repo.sync.set(path, newValue.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, newValue, 1)
  }

  private val i = new AtomicInteger(0)
  def nextPath = "path_" + i.getAndIncrement
  def nextValue = "value_" + i.getAndIncrement
}

class SimpleConfigRepoSpec extends ConfigRepoSpec {
  val repo = new SimpleConfigRepo
}

class ZkConfigRepoSpec extends ConfigRepoSpec with BeforeAndAfterAll {

  var zkServer: TestingServer = _

  val repo = {
    implicit val timer = DefaultTimer.twitter
    zkServer = new TestingServer
    val zkClient = ZkClient(
      connectString = s"127.0.0.1:${zkServer.getPort}",
      sessionTimeout = 4 seconds)
      .withAcl(Seq(new ACL(Perms.ALL, Ids.ANYONE_ID_UNSAFE)))
    new ZkConfigRepo(zkClient)
  }

  override def afterAll() {
    zkServer.close()
  }
}

object TestZkServer {
  def apply() = new TestingServer
}
