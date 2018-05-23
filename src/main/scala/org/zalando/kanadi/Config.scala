package org.zalando.kanadi

import java.net.URI

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import org.zalando.kanadi.models.HttpConfig

import scala.concurrent.duration.FiniteDuration

trait Config {
  private val conf = ConfigFactory.load

  implicit val nakadiUri = new URI(conf.as[String]("kanadi.nakadi.uri"))

  implicit val kanadiHttpConfig = HttpConfig(
    conf.as[Boolean]("kanadi.http-config.censor-oAuth2-token"),
    conf.as[Int]("kanadi.http-config.single-string-chunk-length"),
    conf.as[Int]("kanadi.http-config.event-list-chunk-length"),
    conf.as[FiniteDuration]("kanadi.http-config.no-empty-slots-cursor-reset-retry-delay"),
    conf.as[FiniteDuration]("kanadi.http-config.server-disconnect-retry-delay")
  )

}

object Config extends Config
