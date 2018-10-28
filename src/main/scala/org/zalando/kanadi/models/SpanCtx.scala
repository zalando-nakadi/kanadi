package org.zalando.kanadi.models

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class SpanCtx(ctx: String) extends AnyVal

object SpanCtx {
  implicit val eventIdEncoder: Encoder[SpanCtx] =
    Encoder.instance[SpanCtx](_.ctx.asJson)
  implicit val eventIdDecoder: Decoder[SpanCtx] =
    Decoder[String].map(SpanCtx.apply)
}
