package thirstycrow.genie.mysql

import thirstycrow.genie.JsonConfigSerializer
import thirstycrow.genie.Chained

object MysqlClientConfig {
  def apply(chained: Chained[MysqlClientConfig]) = {
    new MysqlClientConfig(
      host = chained.optional(_.host),
      port = chained.optional(_.port),
      user = chained.optional(_.user),
      password = chained.optional(_.password),
      params = chained.props(_.params))
  }
}

case class MysqlClientConfig(
  host: Option[String] = None,
  port: Option[Int] = None,
  user: Option[String] = None,
  password: Option[String] = None,
  params: Map[String, _] = Map.empty)

class MysqlClientConfigSerializer extends JsonConfigSerializer[MysqlClientConfig]
