package thirstycrow.genie

import com.twitter.conversions.time._
import com.twitter.util.{Future, Promise, Timer, Var}
import com.twitter.zk.{ZkClient, ZNode}
import com.twitter.util.{Return, Throw, Updatable}
import org.apache.zookeeper.KeeperException._

class ZkConfigRepo(val zkClient: ZkClient)(implicit timer: Timer) extends ConfigRepo {

  def get(path: String): Future[Config[Array[Byte]]] = {
    zkClient(regularize(path)).getData()
      .map(node => toConfig(path, node))
      .rescue {
        case ex: NoNodeException => Future.exception(ConfigNotFound(path))
      }
  }

  def monitor(path: String): Var[Future[Config[Array[Byte]]]] = {

    type Content = Future[Config[Array[Byte]]]
    type Container = Var[Content] with Updatable[Content]

    val node = zkClient(regularize(path))
    val firstAttempt = Promise[Config[Array[Byte]]]()
    val v = Var[Future[Config[Array[Byte]]]](firstAttempt)

    def update(f: Future[Config[Array[Byte]]]) {
      if (v.sample().isDefined) v.update(f)
      else firstAttempt.become(f)
    }

    def watch() {
      node.getData.watch().onSuccess {
        case ZNode.Watch(Return(node), nextUpdate) =>
          update(Future(toConfig(path, node)))
          nextUpdate.onSuccess(_ => watch())
        case ZNode.Watch(Throw(ex), _) =>
          if (ex.isInstanceOf[NoNodeException]) {
            update(Future.exception(ConfigNotFound(path)))
          } else {
            update(Future.exception(ex))
          }
          watchCreation(node).onSuccess(_ => watch())
      }.onFailure {
        case e => tryLater(watch())
      }
    }

    def watchCreation(node: ZNode): Future[Unit] = {
      node.parent.getChildren.watch().flatMap {
        case ZNode.Watch(Return(parent), update) =>
          if (parent.children.exists(_.name == node.name)) Future.Done
          else {
            update.unit.before(watchCreation(node))
          }
        case ZNode.Watch(Throw(_: NoNodeException), _) =>
          watchCreation(node.parent).before(watchCreation(node))
        case ZNode.Watch(_, _) =>
          tryLater(watchCreation(node)).flatten
      }.rescue {
        case _ => tryLater(watchCreation(node)).flatten
      }
    }

    def tryLater[T](what: => T) = {
      val retry = timer.doLater(1.second)(what)
      val shutdownHook = sys.addShutdownHook(retry.raise(SystemShutdown))
      retry.ensure(shutdownHook.remove())
    }

    watch()

    v
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

object SystemShutdown extends RuntimeException
