package thirstycrow.genie.mysql

import com.mysql.management.{MysqldResource, MysqldResourceI}
import com.twitter.conversions.time._
import com.twitter.finagle.exp.mysql.OK
import com.twitter.util.Await
import java.io.File
import org.apache.commons.io.FileUtils
import org.scalatest.{ FlatSpec, Matchers }
import redis.embedded.ports.EphemeralPortProvider
import scala.collection.JavaConverters._
import thirstycrow.genie.Genie._
import thirstycrow.genie.ZkConfigRepoSupport
import thirstycrow.genie.AsyncUtils

class FinagleMysqlRecipeSpec extends FlatSpec with Matchers with ZkConfigRepoSupport {

  implicit val timeout = 1 second

  val syncRepo = repo.rich.sync

  override def beforeAll() {
    super.beforeAll()
    EmbeddedMysql.start()

    syncRepo.set("test-db", MysqlClientConfig(
      host = Some("127.0.0.1"),
      port = Some(EmbeddedMysql.port)))
    syncRepo.set("test-db/somebody", MysqlClientConfig(
      user = Some(EmbeddedMysql.USERNAME),
      password = Some(EmbeddedMysql.PASSWORD)))
  }

  it should "get a mysql client" in {
    val mysql = genie.sync.get[MysqlClient]("test-db", "somebody")
    Await.result(mysql.ping())
    mysql.close()
  }

  it should "monitor a mysql client" in {
    val mysql = genie.sync.monitor[MysqlClient]("test-db", "somebody")
    Await.result(mysql.sample().ping())
    EmbeddedMysql.stop()
    intercept[Exception](Await.result(mysql.sample().ping()))
    EmbeddedMysql.start()
    syncRepo.set("test-db", MysqlClientConfig(
      host = Some("127.0.0.1"),
      port = Some(EmbeddedMysql.port)))
    AsyncUtils.keepTrying {
      mysql.sample().ping().map(_.isInstanceOf[OK] shouldBe true)
    }
  }
}

object EmbeddedMysql {

  val MYSQL_DATA_DIR = new File("target/mysql")
  val USERNAME = "username"
  val PASSWORD = "password"

  lazy val mysqld = new MysqldResource(MYSQL_DATA_DIR)

  sys.addShutdownHook {
    mysqld.shutdown()
    FileUtils.deleteQuietly(MYSQL_DATA_DIR)
  }

  def port = mysqld.getPort

  def start() = {
    if (!mysqld.isRunning()) {
      val config = Map(
        MysqldResourceI.PORT -> String.valueOf(new EphemeralPortProvider().next()),
        MysqldResourceI.INITIALIZE_USER -> "true",
        MysqldResourceI.INITIALIZE_USER_NAME -> "username",
        MysqldResourceI.INITIALIZE_PASSWORD -> "password")
      mysqld.start("embedded-mysql", config.asJava)
    }
  }

  def stop() = {
    if (mysqld.isRunning()) {
      mysqld.shutdown()
    }
  }
}
