package thirstycrow.genie.mysql

import thirstycrow.genie.JsonConfigSerializer

case class MysqlClientConfig(
  host: Option[String] = None,
  port: Option[Int] = None,
  user: Option[String] = None,
  password: Option[String] = None,
  params: Map[String, _] = Map.empty)

class MysqlClientConfigSerializer extends JsonConfigSerializer[MysqlClientConfig]
