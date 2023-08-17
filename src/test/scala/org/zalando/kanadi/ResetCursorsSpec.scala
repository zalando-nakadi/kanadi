package org.zalando.kanadi

import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import com.typesafe.config.ConfigFactory
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api.Subscriptions.{CursorWithoutToken, defaultEventStreamSupervisionDecider}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

class ResetCursorsSpec
    extends AsyncFreeTestKitSpec(ActorSystem("ResetCursorsSpec"))
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

  "Create Event Type" in { () =>
    val future = eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))

    future.map(_ => succeed)
  }

  val currentSubscriptionId: Promise[SubscriptionId] = Promise()
  val currentStreamId: Promise[StreamId]             = Promise()
  var events: Option[List[SomeEvent]]                = None
  var eventCounter                                   = 0
  val streamComplete: Promise[Unit]                  = Promise()
  var cursorWithoutToken: Option[CursorWithoutToken] = None

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

  "Start streaming" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    def stream =
      for {
        subscriptionId <- currentSubscriptionId.future
        nakadiSource <- subscriptionsClient.eventsStreamedSourceManaged[SomeEvent](
                          subscriptionId,
                          streamConfig = Subscriptions.StreamConfig(batchFlushTimeout = Some(1 second))
                        )
        finalGraph = nakadiSource.source.toMat(Sink.foreach { subscriptionEvent =>
                       subscriptionEvent.events.getOrElse(List.empty).foreach {
                         case e: Event.Business[SomeEvent] =>
                           if (events.get.contains(e.data)) {
                             eventCounter += 1
                           }
                           if (eventCounter == 2) {
                             cursorWithoutToken = Some(
                               CursorWithoutToken(partition = e.metadata.partition.get,
                                                  offset = "BEGIN",
                                                  eventType = e.metadata.eventType.get))
                             streamComplete.complete(Success(()))
                           }
                         case _ =>
                       }

                     })(Keep.left)
        _ = subscriptionsClient.addStreamToKillSwitch(subscriptionId, nakadiSource.streamId, finalGraph.run())
      } yield nakadiSource.streamId

    stream.onComplete {
      case scala.util.Success(streamId) =>
        pp(streamId)
        currentStreamId.complete(Success(streamId))
      case _ =>
    }

    currentStreamId.future.map(_ => succeed)
  }

  "Publish events" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
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

  "Receive events from source" in { () =>
    streamComplete.future.map(_ => succeed)
  }

  "Close connection" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val closedFuture = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- currentStreamId.future
    } yield subscriptionsClient.closeHttpConnection(subscriptionId, streamId)

    val future = closedFuture

    future.map(_ => succeed)
  }

  "Reset cursors" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val resF = for {
      subscriptionId <- currentSubscriptionId.future
      res <- subscriptionsClient.resetCursors(subscriptionId,
                                              Some(SubscriptionCursorWithoutToken(List(cursorWithoutToken.get))))
    } yield res

    resF.map(result => result mustEqual true)
  }

  "Delete subscription" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val future = for {
      subscriptionId <- currentSubscriptionId.future
      delete         <- subscriptionsClient.delete(subscriptionId)
    } yield delete

    future.map(_ => succeed)
  }

  "Delete event type" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val future = eventsTypesClient.delete(eventTypeName)

    future.map(_ => succeed)
  }

}
