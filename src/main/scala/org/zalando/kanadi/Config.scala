package org.zalando.kanadi

import java.net.URI

import org.zalando.kanadi.models.{ExponentialBackoffConfig, HttpConfig}
import pureconfig._
import pureconfig.generic.auto._

trait Config {
  def config: com.typesafe.config.Config

  lazy val nakadiUri: URI = ConfigSource.default.at("kanadi.nakadi.uri").loadOrThrow[URI]

  implicit lazy val kanadiHttpConfig: HttpConfig = ConfigSource.default.at("kanadi.http-config").loadOrThrow[HttpConfig]

  implicit lazy val kanadiExponentialBackoffConfig: ExponentialBackoffConfig =
    ConfigSource.default.at("kanadi.exponential-backoff-config").loadOrThrow[ExponentialBackoffConfig]
}
