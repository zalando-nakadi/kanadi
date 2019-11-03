package org.zalando.kanadi

import java.util.UUID

import io.circe.{Decoder, Encoder}

case class SomeEvent(firstName: String, lastName: String, uuid: UUID)

object SomeEvent {
  implicit val someEventEncoder: Encoder[SomeEvent] = Encoder.forProduct3(
    "first_name",
    "last_name",
    "uuid"
  )(x => SomeEvent.unapply(x).get)
  implicit val someEventDecoder: Decoder[SomeEvent] = Decoder.forProduct3(
    "first_name",
    "last_name",
    "uuid"
  )(SomeEvent.apply)
}
