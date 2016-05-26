package thirstycrow.genie

import com.twitter.finagle.util.LoadService
import com.twitter.io.Charsets

object ConfigSerializer {

  private val serializers =
    collection.mutable.Map[Manifest[_], ConfigSerializer[_]]() ++=
      LoadService[ConfigSerializer[_]]().map(s => (s.manifest, s))

  def apply[T: Manifest](): ConfigSerializer[T] =
    serializers.get(manifest[T])
      .getOrElse(throw new IllegalStateException(s"No ConfigSerializer available for ${manifest[T]}"))
      .asInstanceOf[ConfigSerializer[T]]

  def add[T: Manifest](serializer: ConfigSerializer[T]) =
    serializers.put(manifest[T], serializer)
}

abstract class ConfigSerializer[T: Manifest] {

  final def manifest = Predef.manifest[T]

  def fromBytes(bytes: Array[Byte]): T

  def toBytes(config: T): Array[Byte]
}

class StringConfigSerializer extends ConfigSerializer[String] {
  def fromBytes(bytes: Array[Byte]) = new String(bytes, Charsets.Utf8)
  def toBytes(config: String): Array[Byte] = config.getBytes(Charsets.Utf8)
}

class BytesConfigSerializer extends ConfigSerializer[Array[Byte]] {
  def fromBytes(bytes: Array[Byte]) = bytes
  def toBytes(bytes: Array[Byte]) = bytes
}
