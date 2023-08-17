package org.zalando.kanadi.api

import java.net.{ServerSocket, URI}
import java.util.UUID
import defaults._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.{complete => _complete, _}
import com.typesafe.config.ConfigFactory
import io.circe._
import org.zalando.kanadi.{AsyncFreeTestKitSpec, Config, PekkoTestKitBase}
import org.zalando.kanadi.models.{EventTypeName, ExponentialBackoffConfig, FlowId, HttpConfig}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import org.zalando.kanadi.api.Events.Errors
import org.mdedetrich.pekko.http.support.CirceHttpSupport._
import org.scalatest.Inside.inside
import org.scalatest.Inspectors
import org.scalatest.matchers.must.Matchers
import pureconfig._
import pureconfig.generic.auto._

import scala.language.postfixOps

class EventPublishRetrySpec
    extends AsyncFreeTestKitSpec(ActorSystem("EventPublishRetrySpec"))
    with PekkoTestKitBase
    with Matchers
    with Config {

  override lazy implicit val kanadiHttpConfig: HttpConfig =
    ConfigSource.default.at("kanadi.http-config").loadOrThrow[HttpConfig].copy(failedPublishEventRetry = true)

  override implicit lazy val kanadiExponentialBackoffConfig: ExponentialBackoffConfig =
    ExponentialBackoffConfig(50 millis, 1.5, 5)

  def getFreePort(): Int = {
    val socket = new ServerSocket(0)
    val port   = socket.getLocalPort
    socket.close()
    port
  }

  val port = getFreePort()

  lazy val config = ConfigFactory.load()

  import scala.util._

  val eventsClient =
    Events(new URI(s"http://localhost:$port"), None)

  sealed abstract class State

  object State {
    case object Initial extends State
    case class RetryFailed(serverFailedEvents: List[Event[EventData]], validationFailedEvent: List[Event[EventData]])
        extends State {
      def failedEvents: List[Event[EventData]] = serverFailedEvents ++ validationFailedEvent
    }
  }

  private val TestEvent = "test-event"

  case class EventData(order: Int)

  object EventData {
    implicit val encoder: Encoder[EventData] = Encoder.forProduct1("order")(x => EventData.unapply(x).get)
    implicit val decoder: Decoder[EventData] = Decoder.forProduct1("order")(EventData.apply)
  }

  var state: State = State.Initial

  val events = (1 to 10).map { index =>
    Event.Business(EventData(index))
  }.toList

  private def randomSplit[A](list: List[A]) = {
    val size         = list.size
    val sizeMinusOne = list.size - 1
    val index = scala.util.Random.nextInt(size) match {
      case 0              => 1
      case `size`         => list.size - 2
      case `sizeMinusOne` => list.size - 2
      case s              => s
    }

    list.splitAt(index)
  }

  val retryWithFailedEventsPromise  = Promise[State.RetryFailed]()
  val retryWithFailedEvents         = retryWithFailedEventsPromise.future
  val retryWithRetriedEventsPromise = Promise[List[Event[EventData]]]()
  val retryWithRetriedEvents        = retryWithRetriedEventsPromise.future

  def routes(runForever: Boolean) =
    pathPrefix("event-types" / TestEvent / "events") {
      pathEndOrSingleSlash {
        post {
          entity(as[List[Event[EventData]]]) { events =>
            state match {
              case State.Initial =>
                val (_, fail) = randomSplit(events)
                // Lets make sure event 10 will always fail with a validation error
                val (validationFailedEvents, serverFailedEvents) = fail.partition(_.data.order == 10)
                val retryFailed = State.RetryFailed(serverFailedEvents, validationFailedEvents)

                state = retryFailed

                _complete(
                  (StatusCodes.MultiStatus,
                   retryFailed.serverFailedEvents.map(event =>
                     Events.BatchItemResponse(
                       event.getMetadata.map(_.eid),
                       Events.PublishingStatus.Aborted,
                       Some(Events.Step.Enriching),
                       None
                     )) ++ retryFailed.validationFailedEvent.map(event =>
                     Events.BatchItemResponse(
                       event.getMetadata.map(_.eid),
                       Events.PublishingStatus.Aborted,
                       Some(Events.Step.Validating),
                       None
                     ))))
              case rf: State.RetryFailed =>
                if (runForever) {
                  val failedEvents = rf.failedEvents.map { event =>
                    Events.BatchItemResponse(
                      event.getMetadata.map(_.eid),
                      Events.PublishingStatus.Aborted,
                      Some(Events.Step.Enriching),
                      None
                    )
                  }
                  _complete((StatusCodes.MultiStatus, failedEvents))
                } else {
                  retryWithFailedEventsPromise.complete(Success(rf))
                  retryWithRetriedEventsPromise.complete(Success(events))
                  _complete(StatusCodes.OK)
                }
            }
          }
        }
      }
    }

  implicit val flowId = FlowId(UUID.randomUUID().toString)

  "Failed partial events are successfully retried" in { () =>
    val future = for {
      bind <- Http(system)
                .newServerAt("localhost", port)
                .bind(routes(false))
                .map(
                  _.addToCoordinatedShutdown(
                    10 seconds
                  ))
      _             <- eventsClient.publish(EventTypeName(TestEvent), events)
      failedEvents  <- retryWithFailedEvents
      retriedEvents <- retryWithRetriedEvents
      _             <- bind.terminate(10 seconds)
    } yield failedEvents.failedEvents.nonEmpty &&
      failedEvents.serverFailedEvents.toSet == retriedEvents.toSet

    future.map(result => result mustEqual true)
  }

  "Retry forever and eventually fail" in { () =>
    val future = for {
      bind <- Http(system)
                .newServerAt("localhost", port)
                .bind(routes(true))
                .map(
                  _.addToCoordinatedShutdown(
                    10 seconds
                  ))
      _ <- eventsClient.publish(EventTypeName(TestEvent), events).recoverWith { case e =>
             bind.terminate(10 seconds).flatMap(_ => Future.failed(e))
           }
    } yield ()

    future.failed.map(result =>
      inside(result) { case e: Errors.EventValidation =>
        Inspectors.forEvery(e.batchItemResponse)(event => event.step must not equal Some(Events.Step.Validating))
      })
  }

}
