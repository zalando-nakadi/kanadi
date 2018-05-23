package org.zalando.kanadi
package models

import akka.http.scaladsl.model.Uri
import io.circe.{Decoder, Encoder}

case class PaginationLink(href: Uri) extends AnyVal

object PaginationLink {
  implicit val linkEncoder: Encoder[PaginationLink] =
    Encoder.forProduct1("href")(_.href)

  implicit val linkDecoder: Decoder[PaginationLink] =
    Decoder.forProduct1("href")(PaginationLink.apply)
}
