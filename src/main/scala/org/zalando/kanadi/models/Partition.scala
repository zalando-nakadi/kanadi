package org.zalando.kanadi.models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class Partition(id: String) extends AnyVal

object Partition {
  implicit val partitionEncoder: Encoder[Partition] = Encoder.instance[Partition](_.id.asJson)
  implicit val partitionDecoder: Decoder[Partition] = Decoder[String].map(Partition.apply)
}
