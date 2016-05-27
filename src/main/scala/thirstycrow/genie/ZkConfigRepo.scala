package thirstycrow.genie

import com.twitter.util.Future
import com.twitter.zk.ZkClient
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.KeeperException.BadVersionException
import com.twitter.zk.ZNode

class ZkConfigRepo(zkClient: ZkClient) extends ConfigRepo {

  def get(path: String): Future[Config[Array[Byte]]] = {
    zkClient(regularize(path)).getData().map { node =>
      Config(path, node.bytes, node.stat.getVersion)
    }.rescue {
      case ex: NoNodeException => Future.exception(ConfigNotFound(path))
    }
  }

  def set(path: String, value: Array[Byte], version: Int): Future[Unit] = {
    zkClient(regularize(path)).setData(value, version).unit.rescue {
      case ex: NoNodeException => Future.exception(ConfigUpdated(path))
      case ex: BadVersionException => Future.exception(ConfigUpdated(path))
    }
  }

  def set(path: String, value: Array[Byte]): Future[Unit] = {
    val node = zkClient(regularize(path))
    node.setData(value, -1).unit.rescue {
      case ex: NoNodeException => create(node, value)
    }
  }

  private def regularize(path: String) = {
    if (path.startsWith("/")) path else "/" + path
  }

  private def create(node: ZNode, value: Array[Byte]): Future[Unit] = {
    node.create(value).unit.rescue {
      case ex: NoNodeException =>
        create(node.parent, Array.empty)
          .before(node.create(value).unit)
    }
  }
}
