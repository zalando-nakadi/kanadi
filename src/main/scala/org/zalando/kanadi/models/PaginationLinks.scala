package org.zalando.kanadi
package models

import io.circe.{Decoder, Encoder}

final case class PaginationLinks(prev: Option[PaginationLink], next: Option[PaginationLink])

object PaginationLinks {
  implicit val paginationLinksEncoder: Encoder[PaginationLinks] =
    Encoder.forProduct2(
      "prev",
      "next"
    )(x => PaginationLinks.unapply(x).get)

  implicit val paginationLinksDecoder: Decoder[PaginationLinks] =
    Decoder.forProduct2(
      "prev",
      "next"
    )(PaginationLinks.apply)
}
