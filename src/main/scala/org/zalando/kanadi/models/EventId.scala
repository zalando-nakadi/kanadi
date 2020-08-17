package org.zalando.kanadi.models

import java.util.UUID

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class EventId(id: UUID) extends AnyVal

object EventId {
  implicit val eventIdEncoder: Encoder[EventId] =
    Encoder.instance[EventId](_.id.asJson)
  implicit val eventIdDecoder: Decoder[EventId] =
    Decoder[UUID].map(EventId.apply)

  def random: EventId = EventId(java.util.UUID.randomUUID())
}
