package org.zalando

import java.net.URI
import cats.syntax.either._
import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import scala.util.control.NonFatal

package object kanadi {
  private[kanadi] implicit val uriEncoder: Encoder[URI] =
    Encoder.instance[URI](_.toString.asJson)

  private[kanadi] implicit val uriDecoder: Decoder[URI] = new Decoder[URI] {
    override def apply(c: HCursor): Result[URI] =
      c.as[String].flatMap { value =>
        try {
          Right(new URI(value))
        } catch {
          case NonFatal(_) => Left(DecodingFailure("Invalid Uri", c.history))
        }
      }
  }
}
