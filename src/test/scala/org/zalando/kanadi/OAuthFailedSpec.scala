package org.zalando.kanadi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.mdedetrich.webmodels.{FlowId, OAuth2Token, OAuth2TokenProvider}
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Skipped
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.api.{Events, Subscriptions}
import org.zalando.kanadi.models._

import scala.concurrent.Future
import scala.concurrent.duration._

class OAuthFailedSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {

  val config = ConfigFactory.load()

  implicit val system       = ActorSystem()
  implicit val http         = Http()
  implicit val materializer = ActorMaterializer()
  val failingOauth2TokenProvider = Some(
    OAuth2TokenProvider(() => Future.successful(OAuth2Token("Failing token")))
  )

  val subscriptionsClient =
    Subscriptions(nakadiUri, failingOauth2TokenProvider)
  val eventsClient = Events(nakadiUri, failingOauth2TokenProvider)

  override def is: SpecStructure = s2"""
    Call to subscriptions list should fail with invalid token   $oAuthCallSubscriptions
    Call to publishEvents should fail with invalid token        $oAuthPublishEvents
  """

  def oAuthCallSubscriptions = Skipped("No way for current Nakadi docker image to detect \"wrong\" tokens")

  def oAuthPublishEvents = Skipped("No way for current Nakadi docker image to detect \"wrong\" tokens")
}
