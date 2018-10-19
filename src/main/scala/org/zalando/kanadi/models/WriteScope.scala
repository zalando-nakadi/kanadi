package org.zalando.kanadi.models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class WriteScope(id: String) extends AnyVal

object WriteScope {
  implicit val writeScopeEncoder: Encoder[WriteScope] =
    Encoder.instance[WriteScope](_.id.asJson)
  implicit val writeScopeDecoder: Decoder[WriteScope] =
    Decoder[String].map(WriteScope.apply)
}
