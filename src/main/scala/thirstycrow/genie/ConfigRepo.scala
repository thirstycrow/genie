package thirstycrow.genie

import com.twitter.util.{Await, Duration, Event, Future, Var}
import java.util.Arrays

trait ConfigRepo {

  def get(path: String): Future[Config[Array[Byte]]]

  def monitor(path: String): Var[Option[Config[Array[Byte]]]]

  def changes(path: String): Event[Config[Array[Byte]]] = {
    monitor(path).changes
      .filter(_.nonEmpty)
      .map(_.get)
      .dedupWith((a, b) => a.version == b.version && Arrays.equals(a.value, b.value))
  }

  def set(path: String, value: Array[Byte]): Future[Unit]

  def set(path: String, value: Array[Byte], version: Int): Future[Unit]

  def del(path: String): Future[Unit]

  def del(path: String, version: Int): Future[Unit]

  object sync {

    def get(path: String)(implicit timeout: Duration) = {
      Await.result(ConfigRepo.this.get(path), timeout)
    }

    def set(path: String, value: Array[Byte])(implicit timeout: Duration) = {
      Await.result(ConfigRepo.this.set(path, value), timeout)
    }

    def set(path: String, value: Array[Byte], version: Int)(implicit timeout: Duration) = {
      Await.result(ConfigRepo.this.set(path, value, version), timeout)
    }

    def del(path: String)(implicit timeout: Duration) = {
      Await.result(ConfigRepo.this.del(path), timeout)
    }

    def del(path: String, version: Int)(implicit timeout: Duration) = {
      Await.result(ConfigRepo.this.del(path, version), timeout)
    }
  }

  object rich {

    def get[T](path: String)(implicit t: T DefaultsTo String, m: Manifest[T]): Future[Config[T]] = {
      ConfigRepo.this.get(path).map(bytes => bytes.map(ConfigSerializer[T]().fromBytes))
    }

    def get[T](paths: String*)(implicit t: T DefaultsTo String, m: Manifest[T]): Future[Chained[T]] = {
      Future.collect(paths.map(get(_)))
        .map(_.map(_.value))
        .map(Chained(_))
    }

    def monitor[T](path: String)(implicit t: T DefaultsTo String, m: Manifest[T]): Var[Option[T]] = {
      ConfigRepo.this.monitor(path).map(_.map(_.map(ConfigSerializer[T]().fromBytes).value))
    }

    def monitor[T](paths: String*)(implicit t: T DefaultsTo String, m: Manifest[T]): Var[Option[Chained[T]]] = {
      Var.collect(paths.map(monitor(_))).map { seqOpt =>
        if (seqOpt.exists(_.isEmpty)) None
        else Some(Chained(seqOpt.map(_.get)))
      }
    }

    def changes[T](path: String)(implicit t: T DefaultsTo String, m: Manifest[T]): Event[Config[T]] = {
      ConfigRepo.this.changes(path)
        .map(_.map(ConfigSerializer[T]().fromBytes))
    }

    def changes[T](paths: String*)(implicit t: T DefaultsTo String, m: Manifest[T]): Event[Chained[T]] = {
      monitor(paths: _*).changes.filter(_.nonEmpty).map(_.get)
    }

    def set[T: Manifest](path: String, value: T): Future[Unit] = {
      ConfigRepo.this.set(path, ConfigSerializer[T]().toBytes(value))
    }

    def set[T: Manifest](path: String, value: T, version: Int): Future[Unit] = {
      ConfigRepo.this.set(path, ConfigSerializer[T]().toBytes(value), version)
    }

    def del(path: String): Future[Unit] = {
      ConfigRepo.this.del(path)
    }

    def del[T: Manifest](path: String, version: Int): Future[Unit] = {
      ConfigRepo.this.del(path, version)
    }

    object sync {

      def get[T: Manifest](path: String)(implicit timeout: Duration) = {
        Await.result(rich.this.get(path), timeout)
      }

      def set[T: Manifest](path: String, value: T)(implicit timeout: Duration) = {
        Await.result(rich.this.set(path, value), timeout)
      }

      def set[T: Manifest](path: String, value: T, version: Int)(implicit timeout: Duration) = {
        Await.result(rich.this.set(path, value, version), timeout)
      }

      def del(path: String)(implicit timeout: Duration) = {
        ConfigRepo.this.sync.del(path)
      }

      def del(path: String, version: Int)(implicit timeout: Duration) = {
        ConfigRepo.this.sync.del(path, version)
      }
    }
  }
}

case class Config[T](path: String, value: T, version: Int) {
  def map[U](conv: T => U): Config[U] = {
    new Config(path, conv(value), version)
  }
}

case class Chained[T](config: Seq[T]) {

  def required[U](name: String, p: T => Option[U]): U = {
    config.reverseIterator.map(p).find(_.isDefined).flatten
      .getOrElse(throw new IllegalStateException(s"A required config property is missing: $name"))
  }

  def required[U](p: T => Option[U]): U = {
    config.reverseIterator.map(p).find(_.isDefined).flatten
      .getOrElse(throw new IllegalStateException(s"A required config property is missing"))
  }

  def optional[U](p: T => Option[U]): Option[U] = {
    config.reverseIterator.map(p).find(_.isDefined).flatten
  }

  def props(p: T => Map[String, _]): Map[String, _] = {
    config.iterator.map(p).reduce(_ ++ _)
  }
}

case class ConfigNotFound(path: String) extends RuntimeException(path)

case class ConfigUpdated(path: String) extends RuntimeException(path)
