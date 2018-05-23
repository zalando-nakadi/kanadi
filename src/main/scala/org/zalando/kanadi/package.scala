package org.zalando

import akka.http.scaladsl.model.Uri
import akka.parboiled2.ParserInput
import cats.syntax.either._
import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

import scala.util.control.NonFatal

package object kanadi {
  private[kanadi] implicit val uriEncoder: Encoder[Uri] =
    Encoder.instance[Uri](_.toString().asJson)

  private[kanadi] implicit val uriDecoder: Decoder[Uri] = new Decoder[Uri] {
    override def apply(c: HCursor): Result[Uri] = {
      c.as[String].flatMap { value =>
        try {
          Right(Uri.parseAbsolute(ParserInput(value)))
        } catch {
          case NonFatal(_) => Left(DecodingFailure("Invalid Uri", c.history))
        }
      }
    }
  }
}
