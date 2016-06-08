package thirstycrow.genie

import com.twitter.concurrent.Broker
import com.twitter.conversions.time._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.zk.ZkClient
import com.twitter.util.{Await, Future}
import java.util.concurrent.atomic.AtomicInteger
import org.apache.curator.test.TestingServer
import org.apache.zookeeper.ZooDefs.{Ids, Perms}
import org.apache.zookeeper.data.ACL
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

abstract class ConfigRepoSpec extends BaseSpec {

  val repo: ConfigRepo

  it should "cause an error when trying to get a missing config" in {
    val path = nextPath
    the[ConfigNotFound] thrownBy result(repo.get(path))
  }

  it should "get an existing config" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "monitor an existing config" in {
    val path = nextPath
    val value = nextValue
    val c = repo.changes(path)

    result(repo.set(path, value.getBytes))
    result(c.toFuture()).map(new String(_)) shouldBe Config(path, value, 0)

    val newValue = nextValue
    result(repo.set(path, newValue.getBytes))
    result(c.toFuture()).map(new String(_)) shouldBe Config(path, newValue, 1)
  }

  it should "monitor a missing config" in {
    val path = Seq.fill(3)(nextPath).mkString("/")
    val value = nextValue
    val m = repo.changes(path)
    result(repo.set(path, value.getBytes))
    result(m.toFuture()).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "cause an error when trying to update a missing config" in {
    val path = nextPath
    val value = nextValue
    the[ConfigUpdated] thrownBy result(repo.set(path, value.getBytes, 0))
    the[ConfigNotFound] thrownBy result(repo.get(path))
  }

  it should "cause an error when trying to update an existing config with unmatching version number" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    the[ConfigUpdated] thrownBy result(repo.set(path, nextValue.getBytes, 1))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "update an existing config with matching version number, and increment the version number" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    val newValue = nextValue
    result(repo.set(path, newValue.getBytes, 0))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, newValue, 1)
  }

  it should "force update a config even if the config node dose not exist" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "force update a config even if any ancestor node dose not exist" in {
    val path = Seq.fill(3)(nextPath).mkString("/")
    val value = nextValue
    result(repo.set(path, value.getBytes))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, value, 0)
  }

  it should "force update a config without a version number" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    val newValue = nextValue
    result(repo.set(path, newValue.getBytes))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, newValue, 1)
  }

  it should "cause an error when trying to delete a config with unmatching version number" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, value, 0)
    intercept[ConfigUpdated] {
      result(repo.del(path, 1))
    }
  }

  it should "delete a config with matching version number" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    result(repo.get(path)).map(new String(_)) shouldBe Config(path, value, 0)
    result(repo.del(path, 0))
    intercept[ConfigNotFound](result(repo.get(path)))
  }

  it should "force delete a config" in {
    val path = nextPath
    val value = nextValue
    result(repo.set(path, value.getBytes))
    result(repo.del(path))
    intercept[ConfigNotFound](result(repo.get(path)))
  }

  it should "set/get config with the rich repo api" in {

    import Recipes._

    val path = nextPath
    val value = nextValue
    result(repo.rich.set(path, value))
    result(repo.rich.get(path)) shouldBe Config(path, value, 0)
    val newValue = nextValue
    result(repo.rich.set(path, newValue, 0))
    result(repo.rich.get(path)) shouldBe Config(path, newValue, 1)
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

    val broker = new Broker[Config[Array[Byte]]]
    repo.changes(path).respond(broker ! _)
    result(repo.set(path, value.getBytes))
    result(broker.recv.sync).map(new String(_)) shouldBe Config(path, value, 0)

    zkServer.stop()

    val newValue = nextValue
    intercept[Exception](result(repo.set(path, newValue.getBytes)))

    zkServer.restart()

    result(repo.set(path, newValue.getBytes))
    result(broker.recv.sync).map(new String(_)) shouldBe Config(path, newValue, 1)
  }
}
