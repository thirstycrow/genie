package thirstycrow.genie

import com.twitter.conversions.time._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util._

object AsyncUtils {

  def keepTrying[T](test: => Future[T]): T = keepTrying(5.seconds)(test)

  def keepTrying[T](timeout: Duration)(test: => Future[T]): T = {

    def check(): Future[T] = {
      test.rescue {
        case ex => DefaultTimer.twitter.doLater(100.millisecond)(check()).flatten
      }
    }

    Await.result(check(), timeout)
  }
}
