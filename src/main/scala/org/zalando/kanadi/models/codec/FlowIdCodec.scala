package org.zalando.kanadi.models.codec

import io.circe._
import io.circe.syntax._
import org.zalando.kanadi.models.FlowId

trait FlowIdCodec {
  implicit val flowIdDecoder: Decoder[FlowId] = Decoder[String].map(FlowId)
  implicit val flowIdEncoder: Encoder[FlowId] = Encoder.instance[FlowId](_.value.asJson)
}

object FlowIdCodec extends FlowIdCodec
