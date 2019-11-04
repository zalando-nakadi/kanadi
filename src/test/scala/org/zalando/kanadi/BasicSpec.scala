package org.zalando.kanadi

import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.mdedetrich.webmodels.FlowId
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.api.Subscriptions.{
  ConnectionClosedCallback,
  EventCallback,
  defaultEventStreamSupervisionDecider
}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Success

class BasicSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {
  override def is: SpecStructure = sequential ^ s2"""
    Create Event Type          $createEventType
    Create Subscription events $createSubscription
    Start streaming            $startStreaming
    Publish events             $publishEvents
    Receive events             $receiveEvents
    Get Subscription stats     $getSubscriptionStats
    Close connection           $closeConnection
    Delete subscription        $deleteSubscription
    Delete event type          $deleteEventType
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
        streamId.pp
        currentStreamId.complete(Success(streamId))
      case _ =>
    }

    currentStreamId.future.map(_ => ()) must be_==(())
      .await(0, timeout = 4 minutes)

  }

  def receiveEvents = (name: String) => {
    streamComplete.future must be_==(()).await(0, timeout = 5 minutes)
  }

  def getSubscriptionStats = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)

    val statsPresent = for {
      subscriptionId <- currentSubscriptionId.future
      stats          <- subscriptionsClient.stats(subscriptionId)
    } yield stats.isDefined

    statsPresent must be_==(true).await(retries = 3, timeout = 10 seconds)
  }

  def closeConnection = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
    val closedFuture = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- currentStreamId.future
    } yield subscriptionsClient.closeHttpConnection(subscriptionId, streamId)

    val waitForCloseFuture =
      akka.pattern.after(3 seconds, system.scheduler)(Future.successful(subscriptionClosed.get()))

    val future = for {
      closed                <- closedFuture
      waitForClose          <- waitForCloseFuture
      modifySourceActivated = modifySourceFunctionActivated.get()
    } yield (closed, waitForClose, modifySourceActivated)

    future must be_==((true, true, true)).await(0, timeout = 1 minute)
  }

  def deleteSubscription = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
    val future = for {
      subscriptionId <- currentSubscriptionId.future
      delete         <- subscriptionsClient.delete(subscriptionId)
    } yield delete

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def deleteEventType = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
    val future = eventsTypesClient.delete(eventTypeName)

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

}
