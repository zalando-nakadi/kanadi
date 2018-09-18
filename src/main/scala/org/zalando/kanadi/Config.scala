package org.zalando.kanadi

import java.net.URI

import net.ceedubs.ficus.Ficus._
import org.zalando.kanadi.models.HttpConfig
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase

trait Config {
  def config: com.typesafe.config.Config

  lazy val nakadiUri: URI = new URI(config.as[String]("kanadi.nakadi.uri"))

  implicit lazy val kanadiHttpConfig: HttpConfig = config.as[HttpConfig]("kanadi.http-config")
}
