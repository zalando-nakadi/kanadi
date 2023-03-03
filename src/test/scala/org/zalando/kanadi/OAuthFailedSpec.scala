package org.zalando.kanadi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import org.specs2.Specification
import org.specs2.execute.Skipped
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.api.{Events, Subscriptions}
import org.zalando.kanadi.models._

import scala.concurrent.Future

class OAuthFailedSpec extends Specification with FutureMatchers with Config {

  val config = ConfigFactory.load()

  implicit val system = ActorSystem()
  implicit val http   = Http()

  val failingauthTokenProvider = Some(
    AuthTokenProvider(() => Future.successful(AuthToken("Failing token")))
  )

  val subscriptionsClient =
    Subscriptions(nakadiUri, failingauthTokenProvider)
  val eventsClient = Events(nakadiUri, failingauthTokenProvider)

  override def is: SpecStructure = s2"""
    Call to subscriptions list should fail with invalid token   $oAuthCallSubscriptions
    Call to publishEvents should fail with invalid token        $oAuthPublishEvents
  """

  def oAuthCallSubscriptions = Skipped("No way for current Nakadi docker image to detect \"wrong\" tokens")

  def oAuthPublishEvents = Skipped("No way for current Nakadi docker image to detect \"wrong\" tokens")
}
