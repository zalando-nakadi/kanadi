package org.zalando.kanadi

import org.mdedetrich.webmodels.FlowId

object Utils {
  def randomFlowId(): FlowId =
    FlowId(java.util.UUID.randomUUID().toString)
}
