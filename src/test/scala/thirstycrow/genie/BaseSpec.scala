package thirstycrow.genie

import org.scalatest.{FlatSpec, Matchers}
import com.twitter.conversions.time._
import com.twitter.util.{Await, Future}

abstract class BaseSpec extends FlatSpec with Matchers {

   protected def result[T](f: Future[T]) = Await.result(f, 1.seconds)
}
