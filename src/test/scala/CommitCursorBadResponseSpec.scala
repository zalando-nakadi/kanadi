import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import io.circe.JsonObject
import org.mdedetrich.webmodels.FlowId
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.Config
import org.zalando.kanadi.api._
import org.zalando.kanadi.models.{EventTypeName, SubscriptionId}

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util._

class CommitCursorBadResponseSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {
  override def is: SpecStructure = sequential ^ s2"""
    Create Event Type          $createEventType
    Create Subscription events $createSubscription
    Stream subscription        $streamSubscriptionId
  """

  val config = ConfigFactory.load()

  implicit val system       = ActorSystem()
  implicit val http         = Http()
  implicit val materializer = ActorMaterializer()

  val eventTypeName = EventTypeName(s"Kanadi-Test-Event-${UUID.randomUUID().toString}")

  eventTypeName.pp

  val OwningApplication = "KANADI"

  val consumerGroup = UUID.randomUUID().toString

  s"Consumer Group: $consumerGroup".pp

  val subscriptionsClient =
    Subscriptions(nakadiUri, None)
  val eventsClient = Events(nakadiUri, None)
  val eventsTypesClient =
    EventTypes(nakadiUri, None)

  val currentSubscriptionId: Promise[SubscriptionId]                             = Promise()
  val successfullyParsedBadCommitResponse: Promise[Option[CommitCursorResponse]] = Promise()

  def createEventType = (name: String) => {
    val future = eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def createSubscription = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
    val future = subscriptionsClient.createIfDoesntExist(
      Subscription(
        None,
        OwningApplication,
        Option(List(eventTypeName)),
        Option(consumerGroup)
      ))

    future.onComplete {
      case Success(subscription) =>
        subscription.id.pp
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map(x => (x.owningApplication, x.eventTypes)) must beEqualTo(
      (OwningApplication, Option(List(eventTypeName))))
      .await(0, timeout = 5 seconds)
  }

  def streamSubscriptionId = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)

    val future = for {
      subscriptionId <- currentSubscriptionId.future
      _ = subscriptionsClient.eventsStreamedSource[JsonObject](subscriptionId).map { nakadiSource =>
        nakadiSource.source
          .map { subscriptionEvent =>
            subscriptionsClient
              .commitCursors(subscriptionId, SubscriptionCursor(List(subscriptionEvent.cursor)), nakadiSource.streamId)
              .onComplete {
                case Success(response) =>
                  successfullyParsedBadCommitResponse.complete(Success(response))
                case Failure(e) =>
                  successfullyParsedBadCommitResponse.complete(Failure(e))
              }

          }
          .runWith(Sink.foreach(_ => ()))
      }
      result <- successfullyParsedBadCommitResponse.future
    } yield result

    future must beSome.await(0, timeout = 2 minutes)
  }
}
