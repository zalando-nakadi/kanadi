package org.zalando.kanadi.models.codec

import io.circe._
import org.zalando.kanadi.models.Problem

trait ProblemCodec {

  implicit val problemDecoder: Decoder[Problem] =
    Decoder.forProduct5(
      "type",
      "title",
      "status",
      "detail",
      "instance"
    )(Problem.apply)

}

object ProblemCodec extends ProblemCodec
