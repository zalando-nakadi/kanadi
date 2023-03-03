package org.zalando.kanadi.models

import scala.concurrent.duration.FiniteDuration

final case class HttpConfig(censorAuthToken: Boolean,
                            singleStringChunkLength: Int,
                            eventListChunkLength: Int,
                            noEmptySlotsCursorResetRetryDelay: FiniteDuration,
                            serverDisconnectRetryDelay: FiniteDuration,
                            failedPublishEventRetry: Boolean)
