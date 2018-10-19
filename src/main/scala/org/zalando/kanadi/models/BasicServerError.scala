package org.zalando.kanadi.models

import io.circe.{Decoder, Encoder}

final case class BasicServerError(error: String, errorDescription: String)

object BasicServerError {
  implicit val basicServerErrorEncoder: Encoder[BasicServerError] =
    Encoder.forProduct2(
      "error",
      "error_description"
    )(x => BasicServerError.unapply(x).get)

  implicit val basicServerErrorDecoder: Decoder[BasicServerError] =
    Decoder.forProduct2(
      "error",
      "error_description"
    )(BasicServerError.apply)
}
