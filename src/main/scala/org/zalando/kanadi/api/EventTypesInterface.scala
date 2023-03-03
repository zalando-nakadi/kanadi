package org.zalando.kanadi.api

import org.zalando.kanadi.models.{EventTypeName, FlowId}

import scala.concurrent.{ExecutionContext, Future}

trait EventTypesInterface {
  def list()(implicit flowId: FlowId = randomFlowId(), executionContext: ExecutionContext): Future[List[EventType]]

  def create(
      eventType: EventType)(implicit flowId: FlowId = randomFlowId(), executionContext: ExecutionContext): Future[Unit]

  def get(name: EventTypeName)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext): Future[Option[EventType]]

  def update(name: EventTypeName, eventType: EventType)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext): Future[Unit]

  def delete(
      name: EventTypeName)(implicit flowId: FlowId = randomFlowId(), executionContext: ExecutionContext): Future[Unit]
}
