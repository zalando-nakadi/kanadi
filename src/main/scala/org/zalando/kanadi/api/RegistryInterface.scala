package org.zalando.kanadi.api

import org.mdedetrich.webmodels.FlowId

import scala.concurrent.{ExecutionContext, Future}

trait RegistryInterface {
  def enrichmentStrategies(implicit flowId: FlowId = randomFlowId(),
                           executionContext: ExecutionContext): Future[List[String]]

  def partitionStrategies(implicit flowId: FlowId = randomFlowId(),
                          executionContext: ExecutionContext): Future[List[PartitionStrategy]]
}
