package org.zalando.kanadi.models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class EventTypeName(name: String) extends AnyVal

object EventTypeName {
  implicit val eventTypeNameEncoder: Encoder[EventTypeName] =
    Encoder.instance[EventTypeName](_.name.asJson)
  implicit val eventTypeNameDecoder: Decoder[EventTypeName] =
    Decoder[String].map(EventTypeName.apply)
}
