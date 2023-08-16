package org.zalando.kanadi.api

import io.circe.Encoder
import org.zalando.kanadi.models.{EventTypeName, FlowId}

import scala.concurrent.{ExecutionContext, Future}

trait EventsInterface {
  def publish[T](name: EventTypeName, events: List[Event[T]], fillMetadata: Boolean = true)(implicit
      encoder: Encoder[T],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Unit]
}
