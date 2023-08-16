package org.zalando.kanadi

import org.zalando.kanadi.models.FlowId

object Utils {
  def randomFlowId(): FlowId =
    FlowId(java.util.UUID.randomUUID().toString)
}
