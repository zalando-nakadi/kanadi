package org.zalando.kanadi

import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Supervision
import com.typesafe.config.ConfigFactory
import io.circe.{Decoder, Encoder}
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api.Subscriptions.{ConnectionClosedCallback, EventCallback, EventStreamContext}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.Success

final case class SomeBadEvent(firstName: String, lastName: Int, uuid: UUID)

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

class BadJsonDecodingSpec
    extends AsyncFreeTestKitSpec(ActorSystem("BadJsonDecodingSpec"))
    with PekkoTestKitBase
    with Matchers
    with Config {

  val config = ConfigFactory.load()

  val eventTypeName = EventTypeName(s"Kanadi-Test-Event-${UUID.randomUUID().toString}")

  pp(eventTypeName)

  val OwningApplication = "KANADI"

  val consumerGroup = UUID.randomUUID().toString

  pp(s"Consumer Group: $consumerGroup")

  val subscriptionsClient =
    Subscriptions(nakadiUri, None)
  val eventsClient = Events(nakadiUri, None)
  val eventsTypesClient =
    EventTypes(nakadiUri, None)

  val currentSubscriptionId: Promise[SubscriptionId] = Promise()
  val currentStreamId: Promise[StreamId]             = Promise()
  var events: Option[List[SomeEvent]]                = None
  val receivedBadEvent: Promise[Unit]                = Promise()
  var subscriptionClosed: Boolean                    = false
  val streamComplete: Promise[Boolean]               = Promise()

  "Create Event Type" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)

    val future = eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))
    future.map(_ => succeed)
  }

  "Create Subscription events" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)

    val future = subscriptionsClient.createIfDoesntExist(
      Subscription(
        None,
        OwningApplication,
        Some(List(eventTypeName)),
        Some(consumerGroup)
      ))

    future.onComplete {
      case scala.util.Success(subscription) =>
        pp(subscription.id)
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map(result =>
      (result.owningApplication, result.eventTypes) mustEqual ((OwningApplication, Some(List(eventTypeName)))))
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

  "Start streaming bad events" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
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
                      // Connection will be already closed
                      subscriptionClosed = true
                    }
                  )
      } yield stream

    stream.onComplete {
      case scala.util.Success(streamId) =>
        pp(streamId)
        currentStreamId.complete(Success(streamId))
      case _ =>
    }

    currentStreamId.future.map(_ => succeed)
  }

  "Publish good events" in { () =>
    val uUIDOne = java.util.UUID.randomUUID()
    val uUIDTwo = java.util.UUID.randomUUID()

    events = Some(
      List(
        SomeEvent("Robert", "Terwilliger", uUIDOne),
        SomeEvent("Die", "Bart, Die", uUIDTwo)
      ))

    val future = eventsClient.publish[SomeEvent](
      eventTypeName,
      events.get.map(x => Event.Business(x))
    )

    future.map(_ => succeed)
  }

  "Receive bad event" in { () =>
    receivedBadEvent.future.map(_ => succeed)
  }

  "Close connection" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val closedFuture = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- currentStreamId.future
    } yield subscriptionsClient.closeHttpConnection(subscriptionId, streamId)

    val waitForCloseFuture =
      org.apache.pekko.pattern.after(6 seconds, system.scheduler)(Future.successful(subscriptionClosed))

    val future = for {
      closed       <- closedFuture
      waitForClose <- waitForCloseFuture
    } yield (closed | waitForClose) // either connection has been closed earlier or from our client side

    future.map(result => result mustEqual true)
  }

  "Delete subscription" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val future = for {
      subscriptionId <- currentSubscriptionId.future
      _              <- subscriptionsClient.delete(subscriptionId)
    } yield ()

    future.map(_ => succeed)
  }

  "Delete event type" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val future = eventsTypesClient.delete(eventTypeName)

    future.map(_ => succeed)
  }

}
