package org.zalando.kanadi

import java.util.UUID
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Keep, Sink}
import com.typesafe.config.ConfigFactory
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.api.Subscriptions.{CursorWithoutToken, defaultEventStreamSupervisionDecider}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

class ResetCursorsSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {
  override def is: SpecStructure = sequential ^ s2"""
    Create Event Type          $createEventType
    Create Subscription events $createSubscription
    Start streaming            $startStreaming
    Publish events             $publishEvents
    Receive events from source $receiveEvents
    Close connection           $closeConnection
    Reset cursors              $resetCursors
    Delete subscription        $deleteSubscription
    Delete event type          $deleteEventType
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
  var eventCounter                                   = 0
  val streamComplete: Promise[Unit]                  = Promise()
  var cursorWithoutToken: Option[CursorWithoutToken] = None

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

  def startStreaming = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
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
        streamId.pp
        currentStreamId.complete(Success(streamId))
      case _ =>
    }

    currentStreamId.future.map(_ => ()) must be_==(())
      .await(0, timeout = 4 minutes)

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

  def receiveEvents = (name: String) => streamComplete.future must be_==(()).await(0, timeout = 5 minutes)

  def closeConnection = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
    val closedFuture = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- currentStreamId.future
    } yield subscriptionsClient.closeHttpConnection(subscriptionId, streamId)

    val future = closedFuture

    future must be_==(true).await(0, timeout = 1 minute)
  }

  def resetCursors = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)
    val resF = for {
      subscriptionId <- currentSubscriptionId.future
      res <- subscriptionsClient.resetCursors(subscriptionId,
                                              Some(SubscriptionCursorWithoutToken(List(cursorWithoutToken.get))))
    } yield res

    resF must be_==(true).await(5, timeout = 5 minute)
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
