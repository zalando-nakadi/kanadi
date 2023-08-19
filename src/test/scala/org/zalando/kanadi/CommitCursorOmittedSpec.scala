package org.zalando.kanadi

import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import io.circe.JsonObject
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api._
import org.zalando.kanadi.models.{EventTypeName, FlowId, SubscriptionId}

import scala.concurrent.Promise
import scala.util._

class CommitCursorOmittedSpec
    extends AsyncFreeTestKitSpec(ActorSystem("CommitCursorOmittedSpec"))
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

  val currentSubscriptionId: Promise[SubscriptionId]                             = Promise()
  val successfullyParsedBadCommitResponse: Promise[Option[CommitCursorResponse]] = Promise()

  "Create Event Type" in { () =>
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
      case Success(subscription) =>
        pp(subscription.id)
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map(result =>
      (result.owningApplication, result.eventTypes) mustEqual ((OwningApplication, Some(List(eventTypeName)))))
  }

  "Stream subscription" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)

    for {
      subscriptionId <- currentSubscriptionId.future
      _ = subscriptionsClient.eventsStreamedSource[JsonObject](subscriptionId).map { nakadiSource =>
            nakadiSource.source
              .map { subscriptionEvent =>
                subscriptionsClient
                  .commitCursors(subscriptionId,
                                 SubscriptionCursor(List(subscriptionEvent.cursor)),
                                 nakadiSource.streamId,
                                 eventBatch = false)
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
    } yield result mustBe empty
  }
}
