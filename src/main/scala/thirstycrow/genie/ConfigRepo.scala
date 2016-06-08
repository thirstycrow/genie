package thirstycrow.genie

import com.twitter.util.{Await, Duration, Event, Future, Var}
import java.util.Arrays

trait ConfigRepo {

  def get(path: String): Future[Config[Array[Byte]]]

  def changes(path: String): Event[Config[Array[Byte]]]

  def set(path: String, value: Array[Byte]): Future[Unit]

  def set(path: String, value: Array[Byte], version: Int): Future[Unit]

  def del(path: String): Future[Unit]

  def del(path: String, version: Int): Future[Unit]

  object rich {

    def get[T](path: String)(implicit t: T DefaultsTo String, conv: ConfigurableRecipe[T]): Future[Config[T]] = {
      ConfigRepo.this.get(path).map(bytes => bytes.map(conv.fromBytes))
    }

    def getChained[T](paths: String*)(implicit t: T, conv: ChainableRecipe[T]): Future[Chained[T]] = {
      Future.collect(toAbsolute(paths).map(ConfigRepo.this.get(_).map(_.value))).map(conv.fromBytes)
    }

    def get[T](paths: String*)(implicit recipe: MultiConfigurableRecipe[T]): Future[T] = {
      Future.collect(toAbsolute(paths).map(ConfigRepo.this.get(_).map(_.value))).map(recipe.fromBytes)
    }

    def changes[T](path: String)(implicit t: T DefaultsTo String, conv: Recipe[T] with ConfigurableRecipe[T]): Event[Config[T]] = {
      val e = ConfigRepo.this.changes(path).map(_.map(conv.fromBytes))
      closeLastOnChange[Config[T]](e, c => conv.close(c.value))
    }

    def changes[T](paths: String*)(implicit t: T DefaultsTo String, conv: Recipe[T] with MultiConfigurableRecipe[T]): Event[T] = {
      val e = toAbsolute(paths).map(ConfigRepo.this.changes)
        .map(_.map(Seq(_)))
        .reduce { (a, b) =>
          a.joinLast(b).map {
            case (a, b) => a ++ b
          }
        }
        .map(cfgs => conv.fromBytes(cfgs.map(_.value)))
      closeLastOnChange(e, conv.close)
    }

    def set[T](path: String, value: T)(implicit conv: SerializableRecipe[T]): Future[Unit] = {
      ConfigRepo.this.set(path, conv.toBytes(value))
    }

    def set[T](path: String, value: T, version: Int)(implicit conv: SerializableRecipe[T]): Future[Unit] = {
      ConfigRepo.this.set(path, conv.toBytes(value), version)
    }

    def del(path: String): Future[Unit] = {
      ConfigRepo.this.del(path)
    }

    def del(path: String, version: Int): Future[Unit] = {
      ConfigRepo.this.del(path, version)
    }

    private def toAbsolute(paths: Seq[String]) = {
      paths.foldLeft(Seq.empty[String]) {
        case (Nil, path) => Seq(path)
        case (seq @ Seq(head, _*), path) => seq :+ s"${head}/${path}"
      }
    }

    private def closeLastOnChange[T](e: Event[T], close: T => Unit) = {
      e.sliding(2).collect {
        case Seq(init) =>
          init
        case Seq(last, current) =>
          close(last)
          current
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
