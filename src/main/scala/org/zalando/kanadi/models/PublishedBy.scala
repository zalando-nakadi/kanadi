package org.zalando.kanadi.models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class PublishedBy(name: String) extends AnyVal

object PublishedBy {
  implicit val publishedByEncoder: Encoder[PublishedBy] =
    Encoder.instance[PublishedBy](_.name.asJson)
  implicit val publishedByDecoder: Decoder[PublishedBy] =
    Decoder[String].map(PublishedBy.apply)
}
