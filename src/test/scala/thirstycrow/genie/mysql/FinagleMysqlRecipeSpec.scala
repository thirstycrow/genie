package thirstycrow.genie.mysql

import com.mysql.management.{MysqldResource, MysqldResourceI}
import com.twitter.concurrent.Broker
import com.twitter.conversions.time._
import com.twitter.util.Await
import io.getquill.naming.SnakeCase
import io.getquill.sources.finagle.mysql.FinagleMysqlSource
import java.io.File
import org.apache.commons.io.FileUtils
import org.scalatest.FlatSpec
import redis.embedded.ports.EphemeralPortProvider
import scala.collection.JavaConverters._
import thirstycrow.genie.{BaseSpec, ZkConfigRepoSupport}

class FinagleMysqlRecipeSpec extends BaseSpec with ZkConfigRepoSupport {

  import thirstycrow.genie.Recipes._

  implicit val timeout = 1 second

  override def beforeAll() {
    super.beforeAll()
    EmbeddedMysql.start()

    result(repo.rich.set("test-db", MysqlClientConfig(
      host = Some("127.0.0.1"),
      port = Some(EmbeddedMysql.port),
      database = Some("test"))))
    result(repo.rich.set("test-db/somebody", MysqlClientConfig(
      user = Some(EmbeddedMysql.USERNAME),
      password = Some(EmbeddedMysql.PASSWORD))))
  }

  it should "get a mysql client" in {
    val mysql = result(repo.rich.get[FinagleMysqlClient]("test-db", "somebody"))
    Await.result(mysql.ping())
    mysql.close()
  }

  it should "monitor a mysql client" in {
    val broker = new Broker[FinagleMysqlClient]()
    repo.rich.changes[FinagleMysqlClient]("test-db", "somebody").respond(broker ! _)
    val mysql = Await.result(broker.recv.sync, timeout)
    Await.result(mysql.ping())

    EmbeddedMysql.stop()
    intercept[Exception](Await.result(mysql.ping(), timeout))

    EmbeddedMysql.start()
    val updatePort = repo.rich.get[MysqlClientConfig]("test-db").flatMap { cfg =>
      repo.rich.set("test-db", cfg.value.copy(port = Some(EmbeddedMysql.port)))
    }
    result(updatePort)
    Await.result(
      Await.result(broker.recv.sync, timeout).ping(),
      timeout)
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
