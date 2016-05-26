package thirstycrow.genie

import com.twitter.util.Future
import scala.collection.mutable

class SimpleConfigRepo extends ConfigRepo {

  private[this] val configs = mutable.Map[String, Config[Array[Byte]]]()

  def get(path: String): Future[Config[Array[Byte]]] = {
    Future {
      configs.get(path) match {
        case Some(config) => config
        case None => throw ConfigNotFound(path)
      }
    }
  }

  def set(path: String, value: Array[Byte]): Future[Unit] = {
    configs.put(path, Config(path, value, currentVersion(path) + 1))
    Future.Done
  }

  def set(path: String, value: Array[Byte], version: Int): Future[Unit] = {
    if (version != currentVersion(path)) {
      throw ConfigUpdated(path)
    }
    configs.put(path, Config(path, value, version + 1))
    Future.Done
  }

  private def currentVersion(path: String) = configs.get(path).map(_.version).getOrElse(0)
}
