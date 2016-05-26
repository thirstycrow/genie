package thirstycrow.genie

import com.twitter.util.Var
import com.twitter.util.Future
import com.twitter.util.Duration
import com.twitter.util.Await

trait ConfigRepo {

  def get(path: String): Future[Config[Array[Byte]]]

  def set(path: String, value: Array[Byte]): Future[Unit]

  object sync {

    def get(path: String)(implicit timeout: Duration) = {
      Await.result(ConfigRepo.this.get(path), timeout)
    }

    def set(path: String, value: Array[Byte])(implicit timeout: Duration) = {
      Await.ready(ConfigRepo.this.set(path, value), timeout)
    }
  }
}

case class Config[T](path: String, value: T) {
  def map[U](conv: T => U): Config[U] = {
    new Config(path, conv(value))
  }
}

case class ConfigNotFound(path: String) extends RuntimeException(path)
