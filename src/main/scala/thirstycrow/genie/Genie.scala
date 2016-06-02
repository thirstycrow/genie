package thirstycrow.genie

import com.twitter.finagle
import com.twitter.util.{Await, Duration, Future, Var}
import com.twitter.finagle.exp.mysql.{Client, Transactions}
import thirstycrow.genie.mysql.{FinagleMysqlRecipe, MysqlClientConfig}

object Genie {

  type MysqlClient = Client with Transactions

  implicit val finagleMysql = FinagleMysqlRecipe
}

class Genie(val repo: ConfigRepo) {

  val syncRepo = repo.sync

  val richRepo = repo.rich

  val syncRichRepo = repo.rich.sync

  def get[T: Manifest](paths: String*)(implicit recipe: Recipe[T]): Future[T] = {
    implicit val m = recipe.m
    repo.rich.get[recipe.Cfg](toAbsolutePaths(paths): _*).map(recipe.convert)
  }

  def monitor[T: Manifest](paths: String*)(implicit recipe: Recipe[T]): Future[Var[T]] = {
    implicit val m = recipe.m
    repo.rich.monitor[recipe.Cfg](toAbsolutePaths(paths): _*).map { cfg =>
      val init = recipe.convert(cfg.sample())
      val changes = cfg.changes.map(cfg => recipe.convert(cfg))
      val result = Var(init, changes)
      recipe match {
        case recipe: CloseableRecipe[T] =>
          result.changes.sliding(2).collect {
            case Seq(init) => None
            case Seq(current, next) => Some(current)
          }.respond {
            last => last.map(recipe.close(_))
          }
      }
      result
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

    def monitor[T: Manifest](paths: String*)(implicit recipe: Recipe[T], timeout: Duration): Var[T] = {
      Await.result(Genie.this.monitor[T](paths: _*))
    }
  }
}

abstract class Recipe[T: Manifest] {

  type Cfg

  val m: Manifest[Cfg]

  def convert(cfg: Chained[Cfg]): T
}

trait CloseableRecipe[T] { self: Recipe[T] =>

  def close(target: T): Unit
}
