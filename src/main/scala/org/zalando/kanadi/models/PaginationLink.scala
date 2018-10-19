package org.zalando.kanadi
package models

import java.net.URI
import io.circe.{Decoder, Encoder}

final case class PaginationLink(href: URI) extends AnyVal

object PaginationLink {
  implicit val linkEncoder: Encoder[PaginationLink] =
    Encoder.forProduct1("href")(_.href)

  implicit val linkDecoder: Decoder[PaginationLink] =
    Decoder.forProduct1("href")(PaginationLink.apply)
}
