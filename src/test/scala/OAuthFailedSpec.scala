import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import io.circe.Json
import org.mdedetrich.webmodels.{FlowId, OAuth2Token, OAuth2TokenProvider}
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.Config
import org.zalando.kanadi.api.{Events, Subscriptions}
import org.zalando.kanadi.models._

import concurrent.duration._
import scala.concurrent.Future

class OAuthFailedSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {

  implicit val system       = ActorSystem()
  implicit val http         = Http()
  implicit val materializer = ActorMaterializer()
  val failingOauth2TokenProvider = Option(
    OAuth2TokenProvider(() => Future.successful(OAuth2Token("Failing token")))
  )

  val subscriptionsClient =
    Subscriptions(nakadiUri, failingOauth2TokenProvider)
  val eventsClient = Events(nakadiUri, failingOauth2TokenProvider)

  override def is: SpecStructure = s2"""
    Call to subscriptions list should fail with invalid token   $oAuthCallSubscriptions
    Call to publishEvents should fail with invalid token        $oAuthPublishEvents
  """

  private def randomFlowId(): FlowId =
    FlowId(java.util.UUID.randomUUID().toString)

  def oAuthCallSubscriptions = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    subscriptionsClient.list() must throwA[GeneralError]
      .await(0, timeout = 3 seconds)
  }

  def oAuthPublishEvents = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    eventsClient
      .publish[Json](EventTypeName("Test"), List.empty) must throwA[GeneralError]
      .await(0, timeout = 3 seconds)
  }
}
