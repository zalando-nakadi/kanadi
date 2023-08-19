package org.zalando.kanadi

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api.{Events, Subscriptions}
import org.zalando.kanadi.models._

import scala.concurrent.Future

// "No way for current Nakadi docker image to detect wrong tokens so tests
// are ignored
class OAuthFailedSpec
    extends AsyncFreeTestKitSpec(ActorSystem("OAuthFailedSpec"))
    with PekkoTestKitBase
    with Matchers
    with Config {

  val config = ConfigFactory.load()

  val failingauthTokenProvider = Some(
    AuthTokenProvider(() => Future.successful(AuthToken("Failing token")))
  )

  val subscriptionsClient =
    Subscriptions(nakadiUri, failingauthTokenProvider)
  val eventsClient = Events(nakadiUri, failingauthTokenProvider)

  "Call to subscriptions list should fail with invalid token" ignore { implicit testData: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    subscriptionsClient.list().failed.map(result => result mustBe a[GeneralError])
  }

  "Call to publishEvents should fail with invalid token" ignore { implicit testData: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    eventsClient
      .publish[Json](EventTypeName("Test"), List.empty)
      .failed
      .map(result => result mustBe a[GeneralError])
  }
}
