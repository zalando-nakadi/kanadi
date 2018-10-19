package org.zalando.kanadi
package models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class StreamId(id: String) extends AnyVal

object StreamId {
  implicit val streamIdEncoder: Encoder[StreamId] =
    Encoder.instance[StreamId](_.id.asJson)
  implicit val streamIdDecoder: Decoder[StreamId] =
    Decoder[String].map(StreamId.apply)
}
