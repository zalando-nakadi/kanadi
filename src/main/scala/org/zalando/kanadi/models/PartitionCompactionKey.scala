package org.zalando.kanadi.models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class PartitionCompactionKey(key: String) extends AnyVal

object PartitionCompactionKey {
  implicit val eventIdEncoder: Encoder[PartitionCompactionKey] =
    Encoder.instance[PartitionCompactionKey](_.key.asJson)
  implicit val eventIdDecoder: Decoder[PartitionCompactionKey] =
    Decoder[String].map(PartitionCompactionKey.apply)

  def random: PartitionCompactionKey = PartitionCompactionKey(java.util.UUID.randomUUID().toString)
}
