package thirstycrow.genie

import com.twitter.finagle
import com.twitter.util.{Await, Duration, Event, Future}
import com.twitter.finagle.exp.mysql.{Client, Transactions}
import thirstycrow.genie.mysql.{FinagleMysqlRecipe, MysqlClientConfig}

object Genie {

  type MysqlClient = Client with Transactions

  implicit val finagleMysql = FinagleMysqlRecipe
}

class Genie(val repo: ConfigRepo) {

  def get[T: Manifest](paths: String*)(implicit recipe: Recipe[T]): Future[T] = {
    implicit val m = recipe.m
    repo.rich.get[recipe.Cfg](toAbsolutePaths(paths): _*).map(recipe.convert)
  }

  def changes[T: Manifest](paths: String*)(implicit recipe: Recipe[T]): Event[T] = {
    implicit val m = recipe.m
    val result = repo.rich.changes[recipe.Cfg](toAbsolutePaths(paths): _*)
      .map(recipe.convert)
    result.sliding(2).collect {
      case Seq(init) =>
        init
      case Seq(last, current) =>
        recipe.close(last)
        current
    }
  }

  private def toAbsolutePaths(paths: Seq[String]) = {
    paths.foldLeft(Seq.empty[String]) {
      case (Nil, path) => Seq(path)
      case (seq @ Seq(head, _*), path) => seq :+ s"${head}/${path}"
    }
  }

  object sync {

    def get[T: Manifest](paths: String*)(implicit recipe: Recipe[T], timeout: Duration): T = {
      Await.result(Genie.this.get[T](paths: _*))
    }
  }
}

abstract class Recipe[T: Manifest] {

  type Cfg

  val m: Manifest[Cfg]

  def convert(cfg: Chained[Cfg]): T

  def close(target: T): Unit
}
