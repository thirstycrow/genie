package thirstycrow.genie.mysql

import com.twitter.finagle
import com.twitter.finagle.exp.mysql.{Client, Transactions}
import thirstycrow.genie.{Chained, MultiConfigurableRecipe, Recipe}

object FinagleMysqlRecipe extends Recipe[FinagleMysqlClient] with MultiConfigurableRecipe[FinagleMysqlClient] {

  def fromBytes(bytes: Seq[Array[Byte]]): FinagleMysqlClient = {
    val cfg = Chained(bytes.map(MysqlClientConfigRecipe.fromBytes))
    val host = cfg.required("host", _.host)
    val port = cfg.optional(_.port).getOrElse(3306)
    val user = cfg.required("user", _.user)
    val password = cfg.required("password", _.password)
    val database = cfg.required("database", _.database)
    finagle.exp.Mysql.client
      .withCredentials(user, password)
      .withDatabase(database)
      .newRichClient(s"${host}:${port}")
  }

  def close(client: FinagleMysqlClient) {
    client.close()
  }
}
