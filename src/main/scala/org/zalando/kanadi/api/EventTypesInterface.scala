package org.zalando.kanadi.api

import org.mdedetrich.webmodels.FlowId
import org.zalando.kanadi.models.EventTypeName

import scala.concurrent.{ExecutionContext, Future}

trait EventTypesInterface {
  def list()(implicit flowId: FlowId = randomFlowId()): Future[List[EventType]]

  def create(eventType: EventType)(implicit flowId: FlowId = randomFlowId()): Future[Unit]

  def get(name: EventTypeName)(implicit flowId: FlowId = randomFlowId()): Future[Option[EventType]]

  def update(name: EventTypeName, eventType: EventType)(implicit flowId: FlowId = randomFlowId()): Future[Unit]

  def delete(name: EventTypeName)(implicit flowId: FlowId = randomFlowId()): Future[Unit]
}
