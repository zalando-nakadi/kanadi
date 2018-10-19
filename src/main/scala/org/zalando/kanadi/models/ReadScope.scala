package org.zalando.kanadi.models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class ReadScope(id: String) extends AnyVal

object ReadScope {
  implicit val readScopeEncoder: Encoder[ReadScope] =
    Encoder.instance[ReadScope](_.id.asJson)
  implicit val readScopeDecoder: Decoder[ReadScope] =
    Decoder[String].map(ReadScope.apply)
}
