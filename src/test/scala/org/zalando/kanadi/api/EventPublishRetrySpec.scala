package org.zalando.kanadi.api

import java.net.URI
import java.util.UUID

import defaults._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import org.mdedetrich.webmodels.FlowId
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.Config
import org.zalando.kanadi.models.{EventTypeName, ExponentialBackoffConfig, HttpConfig}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase
import net.ceedubs.ficus.Ficus._
import org.zalando.kanadi.api.Events.Errors

class EventPublishRetrySpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {

  override lazy implicit val kanadiHttpConfig: HttpConfig =
    config.as[HttpConfig]("kanadi.http-config").copy(failedPublishEventRetry = true)

  override implicit lazy val kanadiExponentialBackoffConfig: ExponentialBackoffConfig =
    ExponentialBackoffConfig(50 millis, 1.5, 5)

  override def is: SpecStructure =
    sequential ^
      s2"""
    Failed partial events are successfully retried $retryPartialEvents
    Retry forever and eventually fail $retryForeverAndFail
  """

  lazy val config = ConfigFactory.load()

  implicit val system       = ActorSystem()
  implicit val http         = Http()
  implicit val materializer = ActorMaterializer()

  import scala.util._

  val eventsClient =
    Events(new URI("http://localhost:8000"), None)

  sealed abstract class State

  object State {

    case object Initial extends State

    case class RetryFailed(failedEvents: List[Event[EventData]]) extends State

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

  val retryWithFailedEventsPromise = Promise[List[Event[EventData]]]
  val retryWithFailedEvents        = retryWithFailedEventsPromise.future

  def routes(runForever: Boolean) =
    pathPrefix("event-types" / TestEvent / "events") {
      pathEndOrSingleSlash {
        post {
          entity(as[List[Event[EventData]]]) { events =>
            state match {
              case State.Initial =>
                val (_, fail) = randomSplit(events)
                val failedEvents = fail.map { event =>
                  if (event.data.order == 10)
                    Events.BatchItemResponse(
                      event.getMetadata.map(_.eid),
                      Events.PublishingStatus.Aborted,
                      Some(Events.Step.Validating),
                      None
                    )
                  else
                    Events.BatchItemResponse(
                      event.getMetadata.map(_.eid),
                      Events.PublishingStatus.Aborted,
                      Some(Events.Step.Enriching),
                      None
                    )
                }

                state = State.RetryFailed(fail)

                complete((StatusCodes.MultiStatus, failedEvents))
              case State.RetryFailed(fail) =>
                if (runForever) {
                  val failedEvents = fail.map { event =>
                    Events.BatchItemResponse(
                      event.getMetadata.map(_.eid),
                      Events.PublishingStatus.Aborted,
                      Some(Events.Step.Enriching),
                      None
                    )
                  }
                  complete((StatusCodes.MultiStatus, failedEvents))
                } else {
                  retryWithFailedEventsPromise.complete(Success(fail))
                  complete(StatusCodes.OK)
                }
            }
          }
        }
      }
    }

  implicit val flowId = FlowId(UUID.randomUUID().toString)

  def retryPartialEvents = {
    val future = for {
      bind         <- Http(system).bindAndHandle(routes(false), "localhost", 8000)
      _            <- eventsClient.publish(EventTypeName(TestEvent), events)
      failedEvents <- retryWithFailedEvents
      _            <- bind.terminate(1 minute)
    } yield failedEvents

    future must not be empty.await(3, 1 minute)
  }

  def retryForeverAndFail = {
    val future = for {
      bind <- Http(system).bindAndHandle(routes(true), "localhost", 8000)
      _ <- eventsClient.publish(EventTypeName(TestEvent), events).recoverWith {
            case e => bind.terminate(1 minute).flatMap(_ => Future.failed(e))
          }
    } yield ()

    future must throwA[Errors.EventValidation]
      .like {
        case e: Errors.EventValidation =>
          forall(e.batchItemResponse)(event => event.step mustNotEqual Some(Events.Step.Validating))
      }
      .await(3, 1 minute)

  }

}
