package org.zalando.kanadi

import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api.Subscriptions.{EventCallback, defaultEventStreamSupervisionDecider}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.Success

class NoEmptySlotsSpec
    extends AsyncFreeTestKitSpec(ActorSystem("NoEmptySlotsSpec"))
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
  val eventCounter                                   = new AtomicInteger(0)
  val subscriptionClosed: AtomicBoolean              = new AtomicBoolean(false)
  val modifySourceFunctionActivated: AtomicBoolean   = new AtomicBoolean(false)
  val streamComplete: Promise[Unit]                  = Promise()

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
        pp(subscription)
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map(result =>
      (result.owningApplication, result.eventTypes) mustEqual ((OwningApplication, Some(List(eventTypeName)))))
  }

  "Start streaming and recover from noEmptySlot" in { implicit td: TestData =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)

    // Start stream One immediately
    val eventualStreamOne = for {
      subscriptionId <- currentSubscriptionId.future
      stream <- subscriptionsClient.eventsStreamedManaged[SomeEvent](
                  subscriptionId,
                  EventCallback.successAlways { _ =>
                    ()
                  }
                )
    } yield stream

    def eventualStreamTwo =
      for {
        subscriptionId <- currentSubscriptionId.future
        stream <- subscriptionsClient.eventsStreamedManaged[SomeEvent](
                    subscriptionId,
                    EventCallback.successAlways { _ =>
                      ()
                    }
                  )
      } yield stream

    for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- eventualStreamOne
      _              <- org.apache.pekko.pattern.after(100 millis, system.scheduler)(Future.successful(()))
      streamTwo       = eventualStreamTwo
      _              <- org.apache.pekko.pattern.after(100 millis, system.scheduler)(Future.successful(()))
      _               = subscriptionsClient.closeHttpConnection(subscriptionId, streamId)
      _              <- streamTwo
    } yield succeed

  }
}
