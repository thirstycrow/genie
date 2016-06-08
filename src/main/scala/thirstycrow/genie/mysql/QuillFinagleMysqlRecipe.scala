package thirstycrow.genie.mysql

import io.getquill.FinagleMysqlSourceConfig
import io.getquill.naming.SnakeCase
import io.getquill.sources.finagle.mysql.FinagleMysqlSource
import thirstycrow.genie.{MultiConfigurableRecipe, Recipe}
import io.getquill.naming.NamingStrategy

class QuillFinagleMysqlRecipe[N <: NamingStrategy : Manifest]
    extends Recipe[FinagleMysqlSource[N]]
    with MultiConfigurableRecipe[FinagleMysqlSource[N]] {

  def fromBytes(bytes: Seq[Array[Byte]]): FinagleMysqlSource[N] = {
    new FinagleMysqlSource(new FinagleMysqlSourceConfig[N]("") {
      override def client = FinagleMysqlRecipe.fromBytes(bytes)
    })
  }

  def close(source: FinagleMysqlSource[N]) {
    source.close()
  }
}
