package org.zalando.kanadi
package models

import java.util.UUID

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class CursorToken(id: UUID) extends AnyVal

object CursorToken {
  implicit val cursorTokenEncoder: Encoder[CursorToken] =
    Encoder.instance[CursorToken](_.id.asJson)
  implicit val cursorTokenDecoder: Decoder[CursorToken] =
    Decoder[UUID].map(CursorToken.apply)
}
