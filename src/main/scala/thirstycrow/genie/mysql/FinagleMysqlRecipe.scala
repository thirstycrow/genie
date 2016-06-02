package thirstycrow.genie.mysql

import com.twitter.finagle
import com.twitter.finagle.exp.mysql.{Client, Transactions}
import thirstycrow.genie.{Chained, CloseableRecipe, Recipe}

object FinagleMysqlRecipe
    extends Recipe[Client with Transactions]
    with CloseableRecipe[Client with Transactions] {

  type Cfg = MysqlClientConfig

  val m = manifest[Cfg]

  def convert(cfg: Chained[MysqlClientConfig]): Client with Transactions = {
    val host = cfg.required("host", _.host)
    val port = cfg.optional(_.port).getOrElse(3306)
    val user = cfg.required("user", _.user)
    val password = cfg.required("password", _.password)
    finagle.exp.Mysql.client
      .withCredentials(user, password)
      .newRichClient(s"${host}:${port}")
  }

  def close(target: Client with Transactions) = target.close()
}
