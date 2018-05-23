package org.zalando.kanadi.models

import scala.concurrent.duration.FiniteDuration

case class HttpConfig(censorOAuth2Token: Boolean,
                      singleStringChunkLength: Int,
                      eventListChunkLength: Int,
                      noEmptySlotsCursorResetRetryDelay: FiniteDuration,
                      serverDisconnectRetryDelay: FiniteDuration)
