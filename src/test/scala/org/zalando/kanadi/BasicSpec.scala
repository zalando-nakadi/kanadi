package org.zalando.kanadi

import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api.Subscriptions.{
  ConnectionClosedCallback,
  EventCallback,
  defaultEventStreamSupervisionDecider
}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.Success

class BasicSpec extends AsyncFreeTestKitSpec(ActorSystem("BasicSpec")) with PekkoTestKitBase with Matchers with Config {

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
  val eventCounter                                   = new AtomicInteger(0)
  val subscriptionClosed: AtomicBoolean              = new AtomicBoolean(false)
  val modifySourceFunctionActivated: AtomicBoolean   = new AtomicBoolean(false)
  val streamComplete: Promise[Unit]                  = Promise()

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
        stream <- subscriptionsClient.eventsStreamedManaged[SomeEvent](
                    subscriptionId,
                    EventCallback.successAlways { eventCallbackData =>
                      eventCallbackData.subscriptionEvent.events
                        .getOrElse(List.empty)
                        .foreach {
                          case e: Event.Business[SomeEvent] =>
                            if (events.get.contains(e.data)) {
                              eventCounter.addAndGet(1)
                            }
                            if (eventCounter.get() == 2)
                              streamComplete.complete(Success(()))
                          case _ =>
                        }
                    },
                    ConnectionClosedCallback { connectionClosedData =>
                      if (connectionClosedData.cancelledByClient)
                        subscriptionClosed.set(true)
                    },
                    Subscriptions.StreamConfig(),
                    Some { source =>
                      modifySourceFunctionActivated.set(true)
                      source
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

  "Receive events" in { () =>
    streamComplete.future.map(_ => succeed)
  }

  "Get Subscription stats" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)

    val statsPresent = for {
      subscriptionId <- currentSubscriptionId.future
      stats          <- subscriptionsClient.stats(subscriptionId)
    } yield stats.isDefined

    statsPresent.map(result => result mustEqual true)
  }

  "Close connection" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)
    val closedFuture = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- currentStreamId.future
    } yield subscriptionsClient.closeHttpConnection(subscriptionId, streamId)

    val waitForCloseFuture =
      org.apache.pekko.pattern.after(3 seconds, system.scheduler)(Future.successful(subscriptionClosed.get()))

    val future = for {
      closed               <- closedFuture
      waitForClose         <- waitForCloseFuture
      modifySourceActivated = modifySourceFunctionActivated.get()
    } yield (closed, waitForClose, modifySourceActivated)

    future.map(result => result mustEqual ((true, true, true)))
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
