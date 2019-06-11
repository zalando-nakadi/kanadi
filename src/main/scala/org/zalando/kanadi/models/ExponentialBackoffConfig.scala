package org.zalando.kanadi.models

import scala.concurrent.duration._

final case class ExponentialBackoffConfig(initialDelay: FiniteDuration, backoffFactor: Double, maxRetries: Int) {

  def calculate(retry: Int, interval: FiniteDuration): FiniteDuration =
    interval * Math.pow(backoffFactor, retry.toDouble) match {
      case f: FiniteDuration => f
      case _                 => throw new Exception("Expected FiniteDuration")
    }
}
