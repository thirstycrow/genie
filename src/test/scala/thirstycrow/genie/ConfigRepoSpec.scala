package thirstycrow.genie

import com.twitter.conversions.time._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.zk.ZkClient
import com.twitter.util.Await
import java.util.concurrent.atomic.AtomicInteger
import org.apache.curator.test.TestingServer
import org.apache.zookeeper.ZooDefs.{Ids, Perms}
import org.apache.zookeeper.data.ACL
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

abstract class ConfigRepoSpec extends FlatSpec with Matchers {

  implicit val timeout = 1 second

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

  it should "monitor an existing config" in {
    val path = nextPath
    val value = nextValue
    val m = repo.monitor(path)
    repo.sync.set(path, value.getBytes)
    val v = Await.result(m, timeout)
    v.sample().map(new String(_)) shouldBe Config(path, value, 0)
    val newValue = nextValue
    repo.sync.set(path, newValue.getBytes)
    Thread.sleep(100)
    v.sample().map(new String(_)) shouldBe Config(path, newValue, 1)
  }

  it should "monitor a missing config" in {
    val path = Seq.fill(3)(nextPath).mkString("/")
    val value = nextValue
    val m = repo.monitor(path)
    repo.sync.set(path, value.getBytes)
    val v = Await.result(m, timeout)
    v.sample().map(new String(_)) shouldBe Config(path, value, 0)
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

  it should "cause an error when trying to delete a config with unmatching version number" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, value, 0)
    intercept[ConfigUpdated] {
      repo.sync.del(path, 1)
    }
  }

  it should "delete a config with matching version number" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    repo.sync.get(path).map(new String(_)) shouldBe Config(path, value, 0)
    repo.sync.del(path, 0)
    intercept[ConfigNotFound](repo.sync.get(path))
  }

  it should "force delete a config" in {
    val path = nextPath
    val value = nextValue
    repo.sync.set(path, value.getBytes)
    repo.sync.del(path)
    intercept[ConfigNotFound](repo.sync.get(path))
  }

  it should "set/get config with the rich repo api" in {
    val path = nextPath
    val value = nextValue
    val richRepo = repo.rich.sync
    richRepo.set(path, value)
    richRepo.get(path) shouldBe Config(path, value, 0)
    val newValue = nextValue
    richRepo.set(path, newValue, 0)
    richRepo.get(path) shouldBe Config(path, newValue, 1)
  }

  private val i = new AtomicInteger(0)
  def nextPath = "path_" + i.getAndIncrement
  def nextValue = "value_" + i.getAndIncrement
}

trait ZkConfigRepoSupport extends BeforeAndAfterAll {

  self: FlatSpec =>

  var zkServer: TestingServer = _

  val repo = {
    implicit val timer = DefaultTimer.twitter
    zkServer = new TestingServer
    val zkClient = ZkClient(
      connectString = s"127.0.0.1:${zkServer.getPort}",
      sessionTimeout = 1 seconds)
      .withAcl(Seq(new ACL(Perms.ALL, Ids.ANYONE_ID_UNSAFE)))
    new ZkConfigRepo(zkClient)
  }

  override def afterAll() {
    zkServer.close()
  }
}

class ZkConfigRepoSpec extends ConfigRepoSpec with ZkConfigRepoSupport {

  it should "keep monitoring a config when the zk server is restored" in {
    val path = Seq.fill(3)(nextPath).mkString("/")
    val value = nextValue

    val m = repo.monitor(path)
    repo.sync.set(path, value.getBytes)
    val v = Await.result(m, timeout)

    v.sample().map(new String(_)) shouldBe Config(path, value, 0)

    zkServer.stop()

    val newValue = nextValue
    intercept[Exception](repo.sync.set(path, newValue.getBytes))

    zkServer.restart()

    repo.sync.set(path, newValue.getBytes)
    Thread.sleep(100)
    v.sample().map(new String(_)) shouldBe Config(path, newValue, 1)
  }
}
