package org.zalando.kanadi.models

import java.util.UUID

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final case class SubscriptionId(id: UUID) extends AnyVal

object SubscriptionId {
  implicit val subscriptionIdEncoder: Encoder[SubscriptionId] =
    Encoder.instance[SubscriptionId](_.id.asJson)
  implicit val subscriptionIdDecoder: Decoder[SubscriptionId] =
    Decoder[UUID].map(SubscriptionId.apply)
}
