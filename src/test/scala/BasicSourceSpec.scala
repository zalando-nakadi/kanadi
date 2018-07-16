import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import org.mdedetrich.webmodels.FlowId
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.Config
import org.zalando.kanadi.api.Subscriptions.{ConnectionClosedCallback, defaultEventStreamSupervisionDecider}
import org.zalando.kanadi.api._
import org.zalando.kanadi.models._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Success

class BasicSourceSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {
  override def is: SpecStructure = sequential ^ s2"""
    Create Event Type          $createEventType
    Create Subscription events $createSubscription
    Start streaming            $startStreaming
    Publish events             $publishEvents
    Receive events from source $receiveEvents
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

  def createEventType = (name: String) => {
    val future = eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  val currentSubscriptionId: Promise[SubscriptionId] = Promise()
  val currentStreamId: Promise[StreamId]             = Promise()
  var events: Option[List[SomeEvent]]                = None
  var eventCounter                                   = 0
  val streamComplete: Promise[Unit]                  = Promise()

  private def randomFlowId(): FlowId =
    FlowId(java.util.UUID.randomUUID().toString)

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
      .await(0, timeout = 5 seconds)
  }

  def startStreaming = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    def stream =
      for {
        subscriptionId <- currentSubscriptionId.future
        nakadiSource <- subscriptionsClient.eventsStreamedSourceManaged[SomeEvent](
                         subscriptionId
                       )
        finalGraph = nakadiSource.source.toMat(Sink.foreach { subscriptionEvent =>
          subscriptionEvent.events.getOrElse(List.empty).foreach {
            case e: Event.Business[SomeEvent] =>
              if (events.get.contains(e.data)) {
                eventCounter += 1
              }
              if (eventCounter == 2)
                streamComplete.complete(Success(()))
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
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
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

  def receiveEvents = (name: String) => {
    streamComplete.future must be_==(()).await(0, timeout = 5 minutes)
  }

  def closeConnection = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val closedFuture = for {
      subscriptionId <- currentSubscriptionId.future
      streamId       <- currentStreamId.future
    } yield subscriptionsClient.closeHttpConnection(subscriptionId, streamId)

    val future = closedFuture

    future must be_==(true).await(0, timeout = 1 minute)
  }

  def deleteSubscription = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val future = for {
      subscriptionId <- currentSubscriptionId.future
      delete         <- subscriptionsClient.delete(subscriptionId)
    } yield delete

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def deleteEventType = (name: String) => {
    implicit val flowId: FlowId = randomFlowId()
    flowId.pp(name)
    val future = eventsTypesClient.delete(eventTypeName)

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

}
