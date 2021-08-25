package org.zalando.kanadi

import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import org.mdedetrich.webmodels.FlowId
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.api.Subscriptions.{EventCallback, defaultEventStreamSupervisionDecider}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Success

class NoEmptySlotsSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {
  override def is: SpecStructure = sequential ^ s2"""
    Create Event Type                            $createEventType
    Create Subscription events                   $createSubscription
    Start streaming and recover from noEmptySlot $startStreaming
    """

  val config = ConfigFactory.load()

  implicit val system = ActorSystem()
  implicit val http   = Http()

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

  def createEventType = (name: String) => {
    val future = eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  val currentSubscriptionId: Promise[SubscriptionId] = Promise()
  val currentStreamId: Promise[StreamId]             = Promise()
  var events: Option[List[SomeEvent]]                = None
  val eventCounter                                   = new AtomicInteger(0)
  val subscriptionClosed: AtomicBoolean              = new AtomicBoolean(false)
  val modifySourceFunctionActivated: AtomicBoolean   = new AtomicBoolean(false)
  val streamComplete: Promise[Unit]                  = Promise()

  def createSubscription = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
    val future = subscriptionsClient.createIfDoesntExist(
      Subscription(
        None,
        OwningApplication,
        Some(List(eventTypeName)),
        Some(consumerGroup)
      ))

    future.onComplete {
      case scala.util.Success(subscription) =>
        subscription.id.pp
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map(x => (x.owningApplication, x.eventTypes)) must beEqualTo((OwningApplication, Some(List(eventTypeName))))
      .await(0, timeout = 5 seconds)
  }

  def publishEvents = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
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

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def startStreaming = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)

    // Start stream One immediately
    val eventualStreamOne = for {
      subscriptionId <- currentSubscriptionId.future
      stream <- subscriptionsClient.eventsStreamedManaged[SomeEvent](
                  subscriptionId,
                  EventCallback.successAlways { eventCallbackData =>
                    eventCallbackData.subscriptionEvent.events
                      .getOrElse(List.empty)
                  }
                )
    } yield stream

    def eventualStreamTwo =
      for {
        subscriptionId <- currentSubscriptionId.future
        stream <- subscriptionsClient.eventsStreamedManaged[SomeEvent](
                    subscriptionId,
                    EventCallback.successAlways { eventCallbackData =>
                      eventCallbackData.subscriptionEvent.events
                        .getOrElse(List.empty)
                    }
                  )
      } yield stream

    val future = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- eventualStreamOne
      _              <- akka.pattern.after(100 millis, system.scheduler)(Future.successful(()))
      streamTwo       = eventualStreamTwo
      _              <- akka.pattern.after(100 millis, system.scheduler)(Future.successful(()))
      _               = subscriptionsClient.closeHttpConnection(subscriptionId, streamId)
      _              <- streamTwo
    } yield ()

    future must be_==(()).await(0, timeout = 4 minutes)

  }
}
