package thirstycrow.genie

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.util.LoadService
import com.twitter.io.Charsets
import com.twitter.util.Try
import thirstycrow.genie.mysql.{FinagleMysqlRecipe, MysqlClientConfigRecipe, QuillFinagleMysqlRecipe}
import io.getquill.naming.SnakeCase

object Recipes {

  implicit object StringConfigSerializer
      extends Recipe[String]
      with ConfigurableRecipe[String]
      with SerializableRecipe[String] {
    override def fromBytes(bytes: Array[Byte]) = new String(bytes, Charsets.Utf8)
    def toBytes(config: String): Array[Byte] = config.getBytes(Charsets.Utf8)
    def close(t: String) {}
  }

  implicit object BytesConfigSerializer
      extends Recipe[Array[Byte]]
      with ConfigurableRecipe[Array[Byte]]
      with SerializableRecipe[Array[Byte]] {
    override def fromBytes(bytes: Array[Byte]) = bytes
    def toBytes(bytes: Array[Byte]) = bytes
    def close(t: Array[Byte]) {}
  }

  implicit lazy val mysqlClientConfigRecipe = optional(MysqlClientConfigRecipe)

  implicit lazy val finagleMysqlRecipe = optional(FinagleMysqlRecipe)

  implicit lazy val quillFinagleMysqlRecipe = optional(new QuillFinagleMysqlRecipe[SnakeCase])

  private def optional[T](recipe: => T): T = {
    Try(recipe).getOrElse {
      throw new IllegalStateException()
    }
  }
}

object Recipe {

  private val serializers =
    collection.mutable.Map[Manifest[_], Recipe[_]]() ++=
      LoadService[Recipe[_]]().map(s => (s.manifest, s))

  def apply[T: Manifest](): Recipe[T] =
    serializers.get(manifest[T])
      .getOrElse(throw new IllegalStateException(s"No ConfigSerializer available for ${manifest[T]}"))
      .asInstanceOf[Recipe[T]]

  def add[T: Manifest](serializer: Recipe[T]) =
    serializers.put(manifest[T], serializer)
}

trait SerializableRecipe[T] {
  def toBytes(config: T): Array[Byte]
}

trait ConfigurableRecipe[T] {
  def fromBytes(bytes: Array[Byte]): T
}

trait ChainableRecipe[T] {
  def fromBytes(bytes: Array[Byte]): T
  def fromBytes(bytes: Seq[Array[Byte]]): Chained[T] = Chained(bytes.map(fromBytes(_)))
}

trait MultiConfigurableRecipe[T] extends ConfigurableRecipe[T] {
  def fromBytes(bytes: Array[Byte]) = fromBytes(Seq(bytes))
  def fromBytes(bytes: Seq[Array[Byte]]): T
}

abstract class Recipe[T: Manifest] {

  final def manifest = Predef.manifest[T]

  def close(t: T): Unit
}

abstract class JsonConfigSerializer[T: Manifest]
    extends Recipe[T]
    with ConfigurableRecipe[T]
    with SerializableRecipe[T] {
  override def fromBytes(bytes: Array[Byte]) = JsonConfigSerializer.parse[T](bytes)
  def toBytes(socket: T) = JsonConfigSerializer.toBytes(socket)
  def close(t: T) {}
}

object JsonConfigSerializer {
  val mapper = new ObjectMapper
  mapper.registerModule(DefaultScalaModule)

  def parse[T: Manifest](bytes: Array[Byte]): T = {
    mapper.readValue(bytes, manifest[T].runtimeClass.asInstanceOf[Class[T]])
  }

  def toBytes(value: Any) = {
    mapper.writeValueAsBytes(value)
  }

  def toString(value: Any) = {
    mapper.writeValueAsString(value)
  }

  def close(t: Array[Byte]) {}
}
