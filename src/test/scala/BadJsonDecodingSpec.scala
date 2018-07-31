import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Supervision}
import io.circe.{Decoder, Encoder}
import org.mdedetrich.webmodels.FlowId
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.Config
import org.zalando.kanadi.api.Subscriptions.{ConnectionClosedCallback, EventCallback, EventStreamContext}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.Success

case class SomeBadEvent(firstName: String, lastName: Int, uuid: UUID)

object SomeBadEvent {
  implicit val someBadEventEncoder: Encoder[SomeBadEvent] =
    Encoder.forProduct3(
      "first_name",
      "last_name",
      "uuid"
    )(x => SomeBadEvent.unapply(x).get)
  implicit val someBadEventDecoder: Decoder[SomeBadEvent] =
    Decoder.forProduct3(
      "first_name",
      "last_name",
      "uuid"
    )(SomeBadEvent.apply)
}

class BadJsonDecodingSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {
  override def is: SpecStructure = sequential ^ s2"""
    This test handles when a decoder fails to parse some JSON
    Create Event Type          $createEventType
    Create Subscription events $createSubscription
    Start streaming bad events $startStreamBadEvents
    Publish good events        $publishGoodEvents
    Receive bad event          $receiveBadEvent
    Close connection           $closeConnection
    Delete subscription        $deleteSubscription
    Delete event type          $deleteEventType
    """

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

  private def randomFlowId(): FlowId =
    FlowId(java.util.UUID.randomUUID().toString)

  def createEventType = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val future = eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  val currentSubscriptionId: Promise[SubscriptionId] = Promise()
  val currentStreamId: Promise[StreamId]             = Promise()
  var events: Option[List[SomeEvent]]                = None
  val receivedBadEvent: Promise[Unit]                = Promise()
  var subscriptionClosed: Boolean                    = false
  val streamComplete: Promise[Boolean]               = Promise()

  def createSubscription = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val future = subscriptionsClient.createIfDoesntExist(
      Subscription(
        None,
        OwningApplication,
        Option(List(eventTypeName)),
        Option(consumerGroup)
      ))

    future.onComplete {
      case scala.util.Success(subscription) =>
        subscription.id.pp
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map(x => (x.owningApplication, x.eventTypes)) must beEqualTo(
      (OwningApplication, Option(List(eventTypeName))))
      .await(0, timeout = 3 seconds)
  }

  implicit val mySupervisionDecider =
    Subscriptions.EventStreamSupervisionDecider { eventStreamContext: EventStreamContext =>
      {
        case parsingException: Subscriptions.EventJsonParsingException =>
          eventStreamContext.subscriptionsClient.commitCursors(
            eventStreamContext.subscriptionId,
            SubscriptionCursor(List(parsingException.subscriptionEventInfo.cursor)),
            eventStreamContext.streamId)

          receivedBadEvent.complete(Success(()))
          Supervision.Resume
        case _ => Supervision.Stop
      }
    }

  def startStreamBadEvents = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    def stream =
      for {
        subscriptionId <- currentSubscriptionId.future
        stream <- subscriptionsClient.eventsStreamedManaged[SomeBadEvent](
                   subscriptionId,
                   EventCallback.successAlways { eventCallbackData =>
                     eventCallbackData.subscriptionEvent.events
                       .getOrElse(List.empty)
                       .foreach { _ =>
                         ()
                       }
                   },
                   ConnectionClosedCallback { connectionClosedData =>
                     if (connectionClosedData.cancelledByClient)
                       subscriptionClosed = true
                   }
                 )
      } yield stream

    stream.onComplete {
      case scala.util.Success(streamId) =>
        streamId.pp
        currentStreamId.complete(Success(streamId))
      case _ =>
    }

    currentStreamId.future.map(_ => ()) must be_==(())
      .await(0, timeout = 4 minutes)

  }

  def publishGoodEvents = (name: String) => {
    val uUIDOne = java.util.UUID.randomUUID()
    val uUIDTwo = java.util.UUID.randomUUID()

    events = Option(
      List(
        SomeEvent("Robert", "Terwilliger", uUIDOne),
        SomeEvent("Die", "Bart, Die", uUIDTwo)
      ))

    val future = eventsClient.publish[SomeEvent](
      eventTypeName,
      events.get.map(x => Event.Business(x))
    )

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def receiveBadEvent = (name: String) => {
    receivedBadEvent.future must be_==(()).await(0, timeout = 5 minutes)
  }

  def closeConnection = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val closedFuture = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- currentStreamId.future
    } yield subscriptionsClient.closeHttpConnection(subscriptionId, streamId)

    val waitForCloseFuture =
      akka.pattern.after(6 seconds, system.scheduler)(Future.successful(subscriptionClosed))

    val future = for {
      closed       <- closedFuture
      waitForClose <- waitForCloseFuture
    } yield (closed, waitForClose)

    future must be_==((true, true)).await(0, timeout = 1 minute)
  }

  def deleteSubscription = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val future = for {
      subscriptionId <- currentSubscriptionId.future
      _              <- subscriptionsClient.delete(subscriptionId)
    } yield ()

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def deleteEventType = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val future = eventsTypesClient.delete(eventTypeName)

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

}
