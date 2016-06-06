package thirstycrow.genie

import com.twitter.conversions.time._
import com.twitter.util._
import com.twitter.zk.{ZkClient, ZNode}
import org.apache.zookeeper.KeeperException._

class ZkConfigRepo(zkClient: ZkClient)(implicit timer: Timer) extends ConfigRepo {

  def get(path: String): Future[Config[Array[Byte]]] = {
    zkClient(regularize(path)).getData()
      .map(node => toConfig(path, node))
      .rescue {
        case ex: NoNodeException => Future.exception(ConfigNotFound(path))
      }
  }

  def monitor(path: String): Var[Option[Config[Array[Byte]]]] = {
    val node = zkClient(regularize(path))

    def watch(): Future[(ZNode.Data, Future[Unit])] = {
      node.getData.watch().flatMap {
        case ZNode.Watch(Return(node), nextUpdate) =>
          Future.value((node, nextUpdate.unit))
        case ZNode.Watch(Throw(ex), _) =>
          watchCreation(node).flatMap(_ => watch())
      }.onFailure {
        case e => tryLater(watch())
      }
    }

    def watchCreation(node: ZNode): Future[Unit] = {
      node.parent.getChildren.watch().flatMap {
        case ZNode.Watch(Return(parent), update) =>
          if (parent.children.exists(_.name == node.name)) Future.Done
          else update.unit.before(watchCreation(node))
        case ZNode.Watch(Throw(_: NoNodeException), _) =>
          watchCreation(node.parent).before(watchCreation(node))
        case ZNode.Watch(_, _) =>
          tryLater(watchCreation(node)).flatten
      }.rescue {
        case _ => tryLater(watchCreation(node)).flatten
      }
    }

    def tryLater[T](what: => T) = {
      timer.doLater(1.second)(what)
    }

    val v = Var.async[Option[(ZNode.Data, Future[Unit])]](None) { updatable =>

      def monitor() {
        watch().onSuccess {
          case result @ (node, nextUpdate) =>
            updatable.update(Some(result))
            nextUpdate.onSuccess(_ => monitor())
        }
      }

      monitor()

      Closable.make { deadline =>
        Future(
          updatable.asInstanceOf[Var[(ZNode.Data, Future[Unit])]]
            .sample()
            ._2
            .raise(new FutureCancelledException))
      }
    }

    v.map(_.map { case (node, _) => toConfig(path, node) })
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

  def del(path: String): Future[Unit] = del(path, -1)

  def del(path: String, version: Int): Future[Unit] = {
    zkClient(regularize(path)).delete(version).unit.rescue {
      case ex: BadVersionException => Future.exception(ConfigUpdated(path))
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

  private def toConfig(path: String, node: ZNode.Data) = {
    Config(path, node.bytes, node.stat.getVersion)
  }
}
