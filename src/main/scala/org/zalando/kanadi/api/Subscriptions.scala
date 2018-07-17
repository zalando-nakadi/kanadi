package org.zalando.kanadi.api

import java.net.URI
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

import akka.NotUsed
import defaults._
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Connection, RawHeader}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.syntax.either._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import enumeratum._
import io.circe.java8.time._
import io.circe.{Decoder, Encoder, JsonObject}
import org.zalando.kanadi.api.defaults._
import org.zalando.kanadi.models._
import org.mdedetrich.akka.stream.support.CirceStreamSupport
import org.mdedetrich.webmodels.{FlowId, OAuth2TokenProvider, Problem}
import org.mdedetrich.webmodels.circe._
import org.zalando.kanadi.models

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

case class Subscription(id: Option[SubscriptionId],
                        owningApplication: String,
                        eventTypes: Option[List[EventTypeName]] = None,
                        consumerGroup: Option[String] = None,
                        createdAt: Option[OffsetDateTime] = None,
                        readFrom: Option[String] = None,
                        initialCursors: Option[List[String]] = None)

object Subscription {
  implicit val subscriptionEncoder: Encoder[Subscription] =
    Encoder.forProduct7(
      "id",
      "owning_application",
      "event_types",
      "consumer_group",
      "created_at",
      "read_from",
      "initial_cursors"
    )(x => Subscription.unapply(x).get)

  implicit val subscriptionDecoder: Decoder[Subscription] =
    Decoder.forProduct7(
      "id",
      "owning_application",
      "event_types",
      "consumer_group",
      "created_at",
      "read_from",
      "initial_cursors"
    )(Subscription.apply)
}

case class SubscriptionQuery(links: PaginationLinks, items: List[Subscription])

object SubscriptionQuery {
  implicit val subscriptionQueryEncoder: Encoder[SubscriptionQuery] =
    Encoder.forProduct2(
      "_links",
      "items"
    )(x => SubscriptionQuery.unapply(x).get)

  implicit val subscriptionQueryDecoder: Decoder[SubscriptionQuery] =
    Decoder.forProduct2(
      "_links",
      "items"
    )(SubscriptionQuery.apply)
}

case class SubscriptionCursor(items: List[Subscriptions.Cursor])

object SubscriptionCursor {
  implicit val subscriptionCursorEncoder: Encoder[SubscriptionCursor] =
    Encoder.forProduct1("items")(x => SubscriptionCursor.unapply(x).get)

  implicit val subscriptionCursorDecoder: Decoder[SubscriptionCursor] =
    Decoder.forProduct1("items")(SubscriptionCursor.apply)
}

case class SubscriptionEventInfo(cursor: Subscriptions.Cursor, info: Option[JsonObject])

object SubscriptionEventInfo {
  implicit val subscriptionEventInfoEncoder: Encoder[SubscriptionEventInfo] =
    Encoder.forProduct2(
      "cursor",
      "info"
    )(x => SubscriptionEventInfo.unapply(x).get)

  implicit val subscriptionEventInfoDecoder: Decoder[SubscriptionEventInfo] =
    Decoder.forProduct2(
      "cursor",
      "info"
    )(SubscriptionEventInfo.apply)
}

case class SubscriptionEventData[T](events: Option[List[Event[T]]])

object SubscriptionEventData {
  implicit def subscriptionEventDataEncoder[T](
      implicit encoder: Encoder[List[Event[T]]]): Encoder[SubscriptionEventData[T]] =
    Encoder.forProduct1(
      "events"
    )(x => SubscriptionEventData.unapply(x).get)

  implicit def subscriptionEventDataDecoder[T](
      implicit decoder: Decoder[List[Event[T]]]): Decoder[SubscriptionEventData[T]] =
    Decoder.forProduct1(
      "events"
    )(SubscriptionEventData.apply)
}

case class SubscriptionEvent[T](cursor: Subscriptions.Cursor, info: Option[JsonObject], events: Option[List[Event[T]]])

object SubscriptionEvent {

  implicit def subscriptionEventEncoder[T](implicit encoder: Encoder[List[Event[T]]]): Encoder[SubscriptionEvent[T]] =
    Encoder.forProduct3(
      "cursor",
      "info",
      "events"
    )(x => SubscriptionEvent.unapply(x).get)

  implicit def subscriptionEventDecoder[T](implicit decoder: Decoder[List[Event[T]]]): Decoder[SubscriptionEvent[T]] =
    Decoder.forProduct3(
      "cursor",
      "info",
      "events"
    )(SubscriptionEvent.apply)
}

case class SubscriptionStats(items: List[Subscriptions.EventTypeStats])

object SubscriptionStats {
  implicit val subscriptionStatsEncoder: Encoder[SubscriptionStats] =
    Encoder.forProduct1("items")(x => SubscriptionStats.unapply(x).get)

  implicit val subscriptionStatsDecoder: Decoder[SubscriptionStats] =
    Decoder.forProduct1("items")(SubscriptionStats.apply)
}

object Subscriptions {
  protected val logger: LoggerTakingImplicit[FlowId] = Logger.takingImplicit[FlowId](Subscriptions.getClass)
  sealed abstract class Errors(problem: Problem) extends GeneralError(problem)

  object Errors {
    case class NoEmptySlotsOrCursorReset(override val problem: Problem) extends Errors(problem)
    case class SubscriptionNotFound(override val problem: Problem)      extends Errors(problem)
  }

  case class EventJsonParsingException(subscriptionEventInfo: SubscriptionEventInfo,
                                       jsonParsingException: CirceStreamSupport.JsonParsingException)
      extends Exception {
    override def getMessage: String = jsonParsingException.getMessage
  }

  case class Cursor(partition: models.Partition, offset: String, eventType: EventTypeName, cursorToken: CursorToken)

  object Cursor {
    implicit val subscriptionEventCursorEncoder: Encoder[Cursor] =
      Encoder.forProduct4("partition", "offset", "event_type", "cursor_token")(x => Cursor.unapply(x).get)
    implicit val subscriptionEventCursorDecoder: Decoder[Cursor] =
      Decoder.forProduct4("partition", "offset", "event_type", "cursor_token")(Cursor.apply)

  }

  case class EventTypeStats(eventType: EventTypeName, partitions: List[EventTypeStats.Partition])

  object EventTypeStats {
    case class Partition(partition: models.Partition,
                         state: Partition.State,
                         unconsumedEvents: Option[Int],
                         streamId: Option[StreamId])

    object Partition {
      sealed abstract class State(val id: String) extends EnumEntry with Product with Serializable {
        override val entryName = id
      }

      object State extends Enum[State] {
        val values = findValues

        case object Unassigned  extends State("unassigned")
        case object Reassigning extends State("reassigning")
        case object Assigned    extends State("assigned")

        implicit val subscriptionsEventTypeStatsPartitionStateEncoder: Encoder[State] = enumeratum.Circe.encoder(State)
        implicit val subscriptionsEventTypeStatsPartitionStateDecoder: Decoder[State] = enumeratum.Circe.decoder(State)
      }

      implicit val subscriptionsEventTypeStatsPartitionEncoder: Encoder[Partition] =
        Encoder
          .forProduct4("partition", "state", "unconsumed_events", "stream_id")(x =>
            (x.partition, x.state, x.unconsumedEvents, x.streamId))

      implicit val subscriptionsEventTypeStatsPartitionDecoder: Decoder[Partition] =
        Decoder.forProduct4("partition", "state", "unconsumed_events", "stream_id")(Partition.apply)

    }

    implicit val subscriptionsEventTypeStatsEncoder: Encoder[EventTypeStats] =
      Encoder.forProduct2(
        "event_type",
        "partitions"
      )(x => EventTypeStats.unapply(x).get)

    implicit val subscriptionsEventTypeStatsDecoder: Decoder[EventTypeStats] =
      Decoder.forProduct2(
        "event_type",
        "partitions"
      )(EventTypeStats.apply)
  }

  /**
    *
    * @param subscriptionEvent
    * @param streamId
    * @param request
    * @param flowId The current flowId, if [[EventCallback.separateFlowId]] is `true` then the new flowId will be here
    *               else it will use the flowId used when [[Subscriptions.eventsStreamed]] is called
    * @tparam T
    */
  case class EventCallbackData[T](subscriptionEvent: SubscriptionEvent[T],
                                  streamId: StreamId,
                                  request: HttpRequest,
                                  flowId: Option[FlowId])

  /**
    *
    * @param separateFlowId Whether to supply a new flowId for each callback request for committing cursors rather
    *                       than using the flowId specified in [[Subscriptions.eventsStreamed]]. If you want to change
    *                       how the flowId is generated, then you can override `generateFlowId`
    *                       (by default it will generate a flowId from a random UUID)
    * @tparam T
    */
  sealed abstract class EventCallback[T](val separateFlowId: Boolean) {
    def generateFlowId: Option[FlowId] = Option(randomFlowId())
  }

  object EventCallback {

    /**
      * Only executes the callback, expects the client to commit the cursor information
      * @param eventCallback
      * @tparam T
      */
    case class simple[T](eventCallback: EventCallbackData[T] => Unit, override val separateFlowId: Boolean = true)
        extends EventCallback[T](separateFlowId)

    /**
      * Will immediately submit the cursor token, regardless if the callback succeeds or not
      * @param eventCallback
      * @tparam T
      */
    case class successAlways[T](eventCallback: EventCallbackData[T] => Unit,
                                override val separateFlowId: Boolean = true)
        extends EventCallback[T](separateFlowId)

    /**
      * Executes the callback in a try-catch block, only submitting the cursor if the predicate evaluates to true
      * @param eventCallback
      * @tparam T
      */
    case class successPredicate[T](eventCallback: EventCallbackData[T] => Boolean,
                                   override val separateFlowId: Boolean = true)
        extends EventCallback[T](separateFlowId)

    /**
      * Executes the callback in a try-catch block, only submitting the cursor if the predicate evaluates to true
      * @param eventCallback
      * @tparam T
      */
    case class successPredicateFuture[T](eventCallback: EventCallbackData[T] => Future[Boolean],
                                         override val separateFlowId: Boolean = true)
        extends EventCallback[T](separateFlowId)

  }

  case class ConnectionClosedData(occurredAt: OffsetDateTime,
                                  subscriptionId: SubscriptionId,
                                  oldStreamId: StreamId,
                                  cancelledByClient: Boolean)

  case class ConnectionClosedCallback(connectionClosedCallback: ConnectionClosedData => Unit)

  /**
    *
    * @param flowId Current flow id
    * @param subscriptionId Current Stream Subscription id
    * @param streamId Current Stream Id
    * @param subscriptionsClient the current subscription client that is being used
    */
  case class EventStreamContext(flowId: FlowId,
                                subscriptionId: SubscriptionId,
                                streamId: StreamId,
                                subscriptionsClient: Subscriptions)

  case class EventStreamSupervisionDecider(private val privateDecider: EventStreamContext => Supervision.Decider) {
    def decider(eventStreamContext: EventStreamContext): Supervision.Decider =
      privateDecider(eventStreamContext)
  }

  implicit def defaultEventStreamSupervisionDecider(
      implicit executionContext: ExecutionContext): EventStreamSupervisionDecider =
    EventStreamSupervisionDecider { eventStreamContext: EventStreamContext =>
      {
        case parsingException: EventJsonParsingException =>
          implicit val flowId: FlowId = eventStreamContext.flowId
          logger.error(
            s"SubscriptionId: ${eventStreamContext.subscriptionId.id.toString}, StreamId: ${eventStreamContext.streamId.id} Unable to parse JSON (committing cursor), subscription event info is ${parsingException.subscriptionEventInfo.toString}",
            parsingException
          )
          eventStreamContext.subscriptionsClient.commitCursors(
            eventStreamContext.subscriptionId,
            SubscriptionCursor(List(parsingException.subscriptionEventInfo.cursor)),
            eventStreamContext.streamId)
          Supervision.Resume
        case termination: EntityStreamException if termination.getMessage.contains("Entity stream truncation") =>
          implicit val flowId: FlowId = eventStreamContext.flowId
          // This often happens when Nakadi is being updated, Nakadi will abruptly disconnect the stream
          logger.warn(
            s"SubscriptionId: ${eventStreamContext.subscriptionId.id.toString}, StreamId: ${eventStreamContext.streamId.id} Stream Abruptly terminated from Nakadi, reconnecting",
            termination
          )
          Supervision.Stop
        case error: Throwable =>
          implicit val flowId: FlowId = eventStreamContext.flowId
          logger.error(
            s"SubscriptionId: ${eventStreamContext.subscriptionId.id.toString}, StreamId: ${eventStreamContext.streamId.id} Critical Error - Stopping Stream",
            error
          )
          Supervision.Stop
      }
    }

  /**
    * Configuration for a stream
    * @param maxUncommittedEvents The amount of uncommitted events Nakadi will stream before pausing the stream. When in paused state and commit comes - the stream will resume. Minimal value is 1.
    * @param batchLimit Maximum number of [[SubscriptionEvent]]'s in each chunk (and therefore per partition) of the stream. If 0 or unspecified will buffer Events indefinitely and flush on reaching of batchFlushTimeout
    * @param streamLimit Maximum number of [[SubscriptionEvent]]'s in this stream (over all partitions being streamed in this connection). If 0 or undefined, will stream batches indefinitely. Stream initialization will fail if streamLimit is lower than batchLimit.
    * @param batchFlushTimeout Maximum time in seconds to wait for the flushing of each chunk (per partition). If the amount of buffered Events reaches batchLimit before this batchFlushTimeout is reached, the messages are immediately flushed to the client and batch flush timer is reset. If 0 or undefined, will assume 30 seconds.
    * @param streamTimeout Maximum time in seconds a stream will live before connection is closed by the server. If 0 or unspecified will stream indefinitely. If this timeout is reached, any pending messages (in the sense of stream_limit) will be flushed to the client. Stream initialization will fail if streamTimeout is lower than batchFlushTimeout.
    * @param streamKeepAliveLimit Maximum number of empty keep alive batches to get in a row before closing the connection. If 0 or undefined will send keep alive messages indefinitely.
    */
  final case class StreamConfig(maxUncommittedEvents: Option[Int] = None,
                                batchLimit: Option[Int] = None,
                                streamLimit: Option[Int] = None,
                                batchFlushTimeout: Option[FiniteDuration] = None,
                                streamTimeout: Option[FiniteDuration] = None,
                                streamKeepAliveLimit: Option[Int] = None)

  /**
    * Nakadi stream represented as an akka-stream [[Source]]
    * @param streamId
    * @param source
    * @param request
    * @tparam T
    */
  final case class NakadiSource[T](streamId: StreamId,
                                   source: Source[SubscriptionEvent[T], UniqueKillSwitch],
                                   request: HttpRequest)

}

case class Subscriptions(baseUri: URI, oAuth2TokenProvider: Option[OAuth2TokenProvider] = None)(
    implicit kanadiHttpConfig: HttpConfig,
    http: HttpExt,
    materializer: Materializer)
    extends {
  protected val logger: LoggerTakingImplicit[FlowId] = Logger.takingImplicit[FlowId](classOf[Subscriptions])
  private val baseUri_                               = Uri(baseUri.toString)

  /**
    * This endpoint creates a subscription for [[org.zalando.kanadi.models.EventTypeName]]'s. The subscription is needed to be able to consume events from EventTypes in a high level way when Nakadi stores the offsets and manages the rebalancing of consuming clients. The subscription is identified by its key parameters (owning_application, event_types, consumer_group). If this endpoint is invoked several times with the same key subscription properties in body (order of even_types is not important) - the subscription will be created only once and for all other calls it will just return the subscription that was already created.
    * @param subscription [[Subscription]] is a high level consumption unit. Subscriptions allow applications to easily scale the number of clients by managing consumed event offsets and distributing load between instances. The key properties that identify subscription are owningApplication, eventTypes and consumerGroup. It's not possible to have two different subscriptions with these properties being the same.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def create(subscription: Subscription)(implicit flowId: FlowId = randomFlowId(),
                                         executionContext: ExecutionContext): Future[Subscription] = {
    val uri = baseUri_.withPath(baseUri_.path / "subscriptions")

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      entity   <- Marshal(subscription).to[RequestEntity]
      request  = HttpRequest(HttpMethods.POST, uri, headers, entity)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[Subscription]
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Attempts to create a subscription if it doesn't already exist, else it will return the currently existing subscription
    * @param subscription
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def createIfDoesntExist(subscription: Subscription)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Subscription] = {
    for {
      subscriptions <- list(Option(subscription.owningApplication), subscription.eventTypes)
      collect = subscriptions.items.filter { returningSubscription =>
        val consumerGroupCheck = subscription.consumerGroup match {
          case None => true
          case consumerGroup =>
            returningSubscription.consumerGroup == consumerGroup
        }

        val idCheck = subscription.id match {
          case None => true
          case id =>
            returningSubscription.id == id
        }

        consumerGroupCheck && idCheck
      }

      head = collect.headOption

      createIfEmpty <- {
        head match {
          case Some(subscription) => Future.successful(subscription)
          case None               => create(subscription)
        }
      }

    } yield createIfEmpty
  }

  /**
    * Lists all subscriptions that exist in a system. List is ordered by creation date/time descending (newest subscriptions come first).
    * @param owningApplication Parameter to filter subscriptions list by owning application. If not specified - the result list will contain subscriptions of all owning applications.
    * @param eventType Parameter to filter subscriptions list by event types. If not specified - the result list will contain subscriptions for all event types. It's possible to provide multiple values like `List(EventTypeName("et1"),EventTypeName("et2"))`, in this case it will show subscriptions having both "et1" and "et2"
    * @param limit maximum number of subscriptions retuned in one page
    * @param offset page offset
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def list(owningApplication: Option[String] = None,
           eventType: Option[List[EventTypeName]] = None,
           limit: Option[Int] = None,
           offset: Option[Int] = None)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[SubscriptionQuery] = {
    val eventsTypesQuery = eventType match {
      case Some(events) =>
        events.map { eventTypeName =>
          ("event_type", eventTypeName.name)
        }
      case None => Seq.empty
    }

    val uri =
      baseUri_
        .withPath(baseUri_.path / "subscriptions")
        .withQuery(
          Query(
            Seq("limit"              -> limit.map(_.toString),
                "offset"             -> offset.map(_.toString),
                "owning_application" -> owningApplication).collect {
              case (k, Some(v)) => (k, v)
            } ++ eventsTypesQuery: _*)
        )

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      request  = HttpRequest(HttpMethods.GET, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[SubscriptionQuery]
        } else {
          response.status match {
            case _ => processNotSuccessful(response)
          }
        }
      }
    } yield result
  }

  /**
    * Returns a subscription identified by id.
    * @param subscriptionId Id of subscription.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def get(subscriptionId: SubscriptionId)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[Subscription]] = {
    val uri = baseUri_
      .withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString)

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      request  = HttpRequest(HttpMethods.GET, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        if (response.status == StatusCodes.NotFound) {
          // TODO: Replace with response.discardEntityBytes once this is resolved: https://github.com/akka/akka-http/issues/1459
          response.entity.dataBytes.runWith(Sink.ignore)
          Future.successful(None)
        } else if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[Subscription]
            .map(Some.apply)
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Deletes a subscription.
    * @param subscriptionId Id of subscription.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def delete(subscriptionId: SubscriptionId)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Unit] = {
    val uri = baseUri_
      .withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString)

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      request  = HttpRequest(HttpMethods.DELETE, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        if (response.status.isSuccess()) {
          // TODO: Replace with response.discardEntityBytes once this is resolved: https://github.com/akka/akka-http/issues/1459
          response.entity.dataBytes.runWith(Sink.ignore)
          Future.successful(())
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Exposes the currently committed offsets of a subscription.
    * @param subscriptionId Id of subscription.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def cursors(subscriptionId: SubscriptionId)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[SubscriptionCursor]] = {
    val uri = baseUri_.withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString / "cursors")

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      request  = HttpRequest(HttpMethods.GET, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        response.status match {
          case StatusCodes.NotFound | StatusCodes.NoContent =>
            // TODO: Replace with response.discardEntityBytes once this is resolved: https://github.com/akka/akka-http/issues/1459
            response.entity.dataBytes.runWith(Sink.ignore)
            Future.successful(None)
          case s if s.isSuccess() =>
            Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
              .to[SubscriptionCursor]
              .map(x => Some(x))
          case _ => processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Endpoint for committing offsets of the subscription. If there is uncommited data, and no commits happen for 60 seconds, then Nakadi will consider the client to be gone, and will close the connection. As long as no events are sent, the client does not need to commit.
    *
    * If the connection is closed, the client has 60 seconds to commit the events it received, from the moment they were sent. After that, the connection will be considered closed, and it will not be possible to do commit with that X-Nakadi-StreamId anymore.
    *
    * When a batch is committed that also automatically commits all previous batches that were sent in a stream for this partition.
    * @param subscriptionId Id of subscription
    * @param subscriptionCursor
    * @param streamId Id of stream which client uses to read events. It is not possible to make a commit for a terminated or none-existing stream. Also the client can't commit something which was not sent to his stream.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def commitCursors(subscriptionId: SubscriptionId, subscriptionCursor: SubscriptionCursor, streamId: StreamId)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[SubscriptionCursor]] = {
    val uri = baseUri_
      .withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString / "cursors")

    val streamHeaders = RawHeader(xNakadiStreamIdHeader, streamId.id.toString) +: baseHeaders(flowId)

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(streamHeaders)
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: streamHeaders
                    }
                }
      entity   <- Marshal(subscriptionCursor).to[RequestEntity]
      request  = HttpRequest(HttpMethods.POST, uri, headers, entity)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        if (response.status == StatusCodes.NotFound) {
          // TODO: Replace with response.discardEntityBytes once this is resolved: https://github.com/akka/akka-http/issues/1459
          response.entity.dataBytes.runWith(Sink.ignore)
          Future.successful(None)
        } else if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[SubscriptionCursor]
            .map(x => Some(x))
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  def resetCursors(subscriptionId: SubscriptionId, subscriptionCursor: Option[SubscriptionCursor] = None)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Boolean] = {
    val uri = baseUri_
      .withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString / "cursors")

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      entity <- {
        subscriptionCursor match {
          case Some(cursor) =>
            Marshal(cursor).to[RequestEntity]
          case None =>
            Future.successful(HttpEntity.Empty)
        }
      }
      request  = HttpRequest(HttpMethods.PATCH, uri, headers, entity)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        if (response.status == StatusCodes.NotFound) {
          // TODO: Replace with response.discardEntityBytes once this is resolved: https://github.com/akka/akka-http/issues/1459
          response.entity.dataBytes.runWith(Sink.ignore)
          Future.successful(false)
        } else if (response.status.isSuccess()) {
          // TODO: Replace with response.discardEntityBytes once this is resolved: https://github.com/akka/akka-http/issues/1459
          response.entity.dataBytes.runWith(Sink.ignore)
          Future.successful(true)
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  def combinedJsonParserGraph[T](implicit decoder: Decoder[List[Event[T]]])
    : Graph[FlowShape[ByteString, Either[Throwable, SubscriptionEvent[T]]], NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      import org.mdedetrich.akka.stream.support.CirceStreamSupport

      implicit def successDecoder[A](implicit decoder: Decoder[A]): Decoder[Success[A]] =
        Decoder.instance[Success[A]] { c =>
          decoder.apply(c).map(Success.apply)
        }

      val broadcast = b.add(Broadcast[ByteString](2))

      // If we decode some JSON, at least make sure we can parse the basic data else we error
      val zipper =
        b.add(ZipWith[SubscriptionEventInfo, Try[SubscriptionEventData[T]], Either[Throwable, SubscriptionEvent[T]]] {
          case (subscriptionEventInfo, subscriptionEventData) =>
            subscriptionEventData match {
              case Success(data) =>
                Right(
                  SubscriptionEvent(
                    subscriptionEventInfo.cursor,
                    subscriptionEventInfo.info,
                    data.events
                  ))
              case Failure(jsonParsingException: CirceStreamSupport.JsonParsingException) =>
                Left(Subscriptions.EventJsonParsingException(subscriptionEventInfo, jsonParsingException))
              case Failure(throwable: Throwable) =>
                Left(throwable)
            }
        })

      broadcast ~> CirceStreamSupport
        .decode[SubscriptionEventInfo] ~> zipper.in0
      broadcast ~> CirceStreamSupport
        .decode[Success[SubscriptionEventData[T]]]
        .recover {
          case parsingException: CirceStreamSupport.JsonParsingException =>
            Failure(parsingException)
        } ~> zipper.in1

      FlowShape(broadcast.in, zipper.out)
    }

  /**
    * NOTE: This is the strict version of [[eventsStreamed]], unless you know what you are doing, you should only be
    * using this method for debugging purposes. If you don't set streamTimeout, this function will never complete, and you will
    * likely run out of memory on your machine.
    *
    * @param subscriptionId Id of subscription.
    * @param maxUncommittedEvents The amount of uncommitted events Nakadi will stream before pausing the stream. When in paused state and commit comes - the stream will resume. Minimal value is 1.
    * @param batchLimit Maximum number of [[SubscriptionEvent]]'s in each chunk (and therefore per partition) of the stream. If 0 or unspecified will buffer Events indefinitely and flush on reaching of batchFlushTimeout
    * @param streamLimit Maximum number of [[SubscriptionEvent]]'s in this stream (over all partitions being streamed in this connection). If 0 or undefined, will stream batches indefinitely. Stream initialization will fail if streamLimit is lower than batchLimit.
    * @param batchFlushTimeout Maximum time in seconds to wait for the flushing of each chunk (per partition). If the amount of buffered Events reaches batchLimit before this batchFlushTimeout is reached, the messages are immediately flushed to the client and batch flush timer is reset. If 0 or undefined, will assume 30 seconds.
    * @param streamTimeout Maximum time in seconds a stream will live before connection is closed by the server. If 0 or unspecified will stream indefinitely. If this timeout is reached, any pending messages (in the sense of stream_limit) will be flushed to the client. Stream initialization will fail if streamTimeout is lower than batchFlushTimeout.
    * @param streamKeepAliveLimit Maximum number of empty keep alive batches to get in a row before closing the connection. If 0 or undefined will send keep alive messages indefinitely.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @tparam T
    * @return
    */
  def eventsStrictUnsafe[T](subscriptionId: SubscriptionId,
                            maxUncommittedEvents: Option[Int] = None,
                            batchLimit: Option[Int] = None,
                            streamLimit: Option[Int] = None,
                            batchFlushTimeout: Option[FiniteDuration] = None,
                            streamTimeout: Option[FiniteDuration] = None,
                            streamKeepAliveLimit: Option[Int] = None)(
      implicit decoder: Decoder[List[Event[T]]],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[List[SubscriptionEvent[T]]] = {
    val uri = baseUri_
      .withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString / "events")
      .withQuery(
        Query(
          Map(
            "max_uncommitted_events" -> maxUncommittedEvents.map(_.toString),
            "batch_limit"            -> batchLimit.map(_.toString),
            "stream_limit"           -> streamLimit.map(_.toString),
            "batch_flush_timeout"    -> batchFlushTimeout.map(_.toSeconds.toString),
            "stream_timeout"         -> streamTimeout.map(_.toSeconds.toString),
            "stream_keep_alive_limit" -> streamKeepAliveLimit
              .map(_.toString)
          ).collect {
            case (k, Some(v)) => (k, v)
          })
      )

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      request = HttpRequest(HttpMethods.GET, uri, headers)
      _       = logger.debug(request.toString)
      // Create a single connection to avoid the pool

      connectionFlow = {
        val host = baseUri_.authority.host.toString()
        if (request.uri.scheme.equalsIgnoreCase("https"))
          http.outgoingConnectionHttps(host)
        else http.outgoingConnection(host)
      }
      response <- Source
                   .single(request)
                   .via(connectionFlow)
                   .runWith(Sink.head)
                   .map(decodeCompressed)
      result <- {
        response.status match {
          case StatusCodes.NotFound =>
            Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
              .to[Problem]
              .map(x => throw Subscriptions.Errors.SubscriptionNotFound(x))
          case StatusCodes.Conflict =>
            Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
              .to[Problem]
              .map(x => throw Subscriptions.Errors.NoEmptySlotsOrCursorReset(x))
          case _ =>
            if (response.status.isSuccess()) {
              for {
                string <- Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
                           .to[String]
                result <- {
                  Source(
                    string
                      .grouped(kanadiHttpConfig.singleStringChunkLength)
                      .map(ByteString(_))
                      .to[List])
                    .via(Framing.delimiter(ByteString("\n"), Int.MaxValue, allowTruncation = true))
                    .via(combinedJsonParserGraph)
                    .map {
                      case Left(error)   => throw error
                      case Right(result) => result
                    }
                    .limit(kanadiHttpConfig.eventListChunkLength)
                    .runWith(Sink.seq)
                }
              } yield result.to[List]
            } else {
              processNotSuccessful(response)
            }
        }
      }
    } yield result
  }

  private final val killSwitches =
    new ConcurrentHashMap[(SubscriptionId, StreamId), UniqueKillSwitch]().asScala

  def addStreamToKillSwitch(subscriptionId: SubscriptionId,
                            streamId: StreamId,
                            uniqueKillSwitch: UniqueKillSwitch): Unit =
    killSwitches((subscriptionId, streamId)) = uniqueKillSwitch

  private def getStreamUri(subscriptionId: SubscriptionId, streamConfig: Subscriptions.StreamConfig) = {
    baseUri_
      .withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString / "events")
      .withQuery(
        Query(
          Map(
            "max_uncommitted_events" -> streamConfig.maxUncommittedEvents.map(_.toString),
            "batch_limit"            -> streamConfig.batchLimit.map(_.toString),
            "stream_limit"           -> streamConfig.streamLimit.map(_.toString),
            "batch_flush_timeout"    -> streamConfig.batchFlushTimeout.map(_.toSeconds.toString),
            "stream_timeout"         -> streamConfig.streamTimeout.map(_.toSeconds.toString),
            "stream_keep_alive_limit" -> streamConfig.streamKeepAliveLimit
              .map(_.toString)
          ).collect {
            case (k, Some(v)) => (k, v)
          })
      )
  }

  private def getBaseHeaders(implicit flowId: FlowId): List[HttpHeader] = {
    baseHeaders(flowId) :+ Connection("Keep-Alive")
  }

  /**
    * Starts a new stream for reading events from this subscription. The data will be automatically rebalanced between
    * streams of one subscription. The minimal consumption unit is a partition, so it is possible to start as many
    * streams as the total number of partitions in event-types of this subscription. The rebalance currently only
    * operates with the number of partitions so the amount of data in event-types/partitions is not considered
    * during autorebalance. The position of the consumption is managed by Nakadi. The client is required to commit
    * the cursors he gets in a stream.
    *
    * This exposes the stream as a [[Source]], which also means you need to handle adding the stream to the kill switch
    * configuration using the [[addStreamToKillSwitch]] method if you plan on using the [[closeHttpConnection]] to kill
    * a stream.
    * @param subscriptionId
    * @param connectionClosedCallback
    * @param streamConfig
    * @param decoder
    * @param flowId
    * @param executionContext
    * @param eventStreamSupervisionDecider
    * @tparam T
    * @return
    */
  def eventsStreamedSource[T](subscriptionId: SubscriptionId,
                              connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                                Subscriptions.ConnectionClosedCallback { _ =>
                                  ()
                                },
                              streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig())(
      implicit
      decoder: Decoder[List[Event[T]]],
      flowId: FlowId,
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider)
    : Future[Subscriptions.NakadiSource[T]] = {
    val uri           = getStreamUri(subscriptionId, streamConfig)
    val streamHeaders = getBaseHeaders

    def cleanup(streamId: StreamId, cancelledByClient: Boolean) = {
      // Cleaning up the connection afterwards
      logger.info(s"SubscriptionId: ${subscriptionId.id}, StreamId: ${streamId.id} HTTP connection closed, cleaning up")
      val connectionClosedData = Subscriptions.ConnectionClosedData(
        OffsetDateTime.now(),
        subscriptionId,
        streamId,
        cancelledByClient
      )
      connectionClosedCallback.connectionClosedCallback(connectionClosedData)
      try {
        killSwitches.remove((subscriptionId, streamId))
      } catch {
        case NonFatal(e) =>
          logger.warn(
            s"SubscriptionId: ${subscriptionId.id}, StreamId: ${streamId.id}, error removing HTTP connection from pool",
            e)
      }
    }

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(streamHeaders)
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: streamHeaders
                    }
                }

      request = HttpRequest(HttpMethods.GET, uri, headers)
      _       = logger.debug(request.toString)

      connectionPoolSettings = ConnectionPoolSettings(http.system)
      clientConnectionSettings = connectionPoolSettings.connectionSettings
        .withIdleTimeout(
          streamConfig.streamTimeout match {
            case Some(finiteDuration) => finiteDuration * 1.1
            case None                 => Duration.Inf
          }
        )

      // Create a single connection to avoid the pool

      connectionFlow = {
        val host = baseUri_.authority.host.toString()
        val port = baseUri_.authority.port
        if (request.uri.scheme.equalsIgnoreCase("https"))
          http.outgoingConnectionHttps(host = host, port = port, settings = clientConnectionSettings)
        else http.outgoingConnection(host = host, port = port, settings = clientConnectionSettings)
      }

      response <- Source
                   .single(request)
                   .via(connectionFlow)
                   .runWith(Sink.head)
                   .map(decodeCompressed)

      result <- {
        if (response.status.isSuccess()) {
          val streamId = (for {
            asString <- response.headers.find(_.is(xNakadiStreamIdHeader.toLowerCase))
          } yield StreamId(asString.value())).getOrElse(
            throw new ExpectedHeader(xNakadiStreamIdHeader, request, response)
          )

          val graph = response.entity.dataBytes
            .via(Framing
              .delimiter(ByteString("\n"), Int.MaxValue, allowTruncation = true))
            .viaMat(KillSwitches.single)(Keep.right)
            .alsoTo(Sink.onComplete { data =>
              val cancelledByClient = data match {
                case util.Failure(CancelledByClient(_, _)) => true
                case _                                     => false
              }
              cleanup(streamId, cancelledByClient)
            })
            .map { data =>
              logger.debug(
                s"SubscriptionId: ${subscriptionId.id.toString}, StreamId: ${streamId.id} Truncated Json data is ${data.utf8String}")
              data
            }
            .via(combinedJsonParserGraph)
            .map {
              case Left(error)   => throw error
              case Right(result) => result
            }
            .withAttributes(ActorAttributes.supervisionStrategy(eventStreamSupervisionDecider
              .decider(Subscriptions
                .EventStreamContext(flowId, subscriptionId, streamId, this))))

          Future.successful(Subscriptions.NakadiSource(streamId, graph, request))
        } else {
          response.status match {
            case StatusCodes.NotFound =>
              Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
                .to[Problem]
                .map(x => throw Subscriptions.Errors.SubscriptionNotFound(x))
            case StatusCodes.Conflict =>
              Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
                .to[Problem]
                .map(x => throw Subscriptions.Errors.NoEmptySlotsOrCursorReset(x))
            case _ =>
              processNotSuccessful(response)
          }
        }
      }
    } yield result
  }

  /**
    * Starts a new stream for reading events from this subscription. The data will be automatically rebalanced between streams of one subscription. The minimal consumption unit is a partition, so it is possible to start as many streams as the total number of partitions in event-types of this subscription. The rebalance currently only operates with the number of partitions so the amount of data in event-types/partitions is not considered during autorebalance. The position of the consumption is managed by Nakadi. The client is required to commit the cursors he gets in a stream.
    *
    * This call lets you register a callback which gets execute every time an event is streamed. There are different types of callbacks depending on how you want to handle failure. The timeout for the akka http request is the same as streamTimeout with a small buffer. Note that typically clients
    * should be using [[eventsStreamedManaged]] as this will handle disconnects/reconnects
    *
    * @param subscriptionId Id of subscription.
    * @param eventCallback The callback which gets executed every time an event is processed via the stream
    * @param connectionClosedCallback The callback which gets executed when the connection is closed, you typically want to reopen the subscription
    * @param streamConfig Configuration for the stream
    * @param eventStreamSupervisionDecider The supervision decider which decides what to do when an error is thrown in the stream. Default behaviour is if its a JSON Circe decoding exception on event data, it will commit the cursor, log the error and resume the stream, otherwise it will log the error and restart the stream.
    * @param modifySourceFunction Allows you to specify a function which modifies the underlying stream
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @tparam T
    * @return The StreamId for this Stream
    */
  def eventsStreamed[T](subscriptionId: SubscriptionId,
                        eventCallback: Subscriptions.EventCallback[T],
                        connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                          Subscriptions.ConnectionClosedCallback { _ =>
                            ()
                          },
                        streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig(),
                        modifySourceFunction: Option[(Source[SubscriptionEvent[T], UniqueKillSwitch]) => (
                          Source[SubscriptionEvent[T],
                                 UniqueKillSwitch])] = None)(
      implicit decoder: Decoder[List[Event[T]]],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider
  ): Future[StreamId] = {

    @inline def sourceWithAdjustments(source: Source[SubscriptionEvent[T], UniqueKillSwitch]) =
      modifySourceFunction match {
        case None             => source
        case Some(sourceFunc) => sourceFunc(source)
      }

    def handleSubscriptionEvent(streamId: StreamId, request: HttpRequest, subscriptionEvent: SubscriptionEvent[T]) = {
      val currentFlowId = if (eventCallback.separateFlowId) {
        eventCallback.generateFlowId
      } else {
        Option(flowId)
      }

      @inline def logDetails =
        s"SubscriptionId: ${subscriptionId.id.toString}, StreamId: ${streamId.id}, CursorToken: ${subscriptionEvent.cursor.cursorToken}, Partition: ${subscriptionEvent.cursor.partition.id}"

      @inline def logAlwaysSuccess() =
        logger.debug(s"$logDetails Committing cursors")

      @inline def logPredicateTrue() =
        logger.debug(s"$logDetails Success predicate is true, committing cursors")

      @inline def logPredicateFalse() =
        logger.debug(s"$logDetails Success predicate is false, not committing cursors")

      @inline def logPredicateFailure(e: Throwable) =
        logger.error(s"$logDetails Success predicate failed with exception, not committing cursors", e)
      @inline def logCallback() =
        logger.debug(s"$logDetails Executed callback")
      @inline def logCallbackFailure(e: Throwable) =
        logger.debug(s"$logDetails Failure executing callback, $e")

      eventCallback match {
        case Subscriptions.EventCallback.simple(cb, _) =>
          try {
            cb(
              Subscriptions.EventCallbackData(
                subscriptionEvent,
                streamId,
                request,
                currentFlowId
              ))
            logCallback()
          } catch {
            case NonFatal(e) => logCallbackFailure(e)
          }

        case Subscriptions.EventCallback.successAlways(cb, _) =>
          commitCursors(subscriptionId, SubscriptionCursor(List(subscriptionEvent.cursor)), streamId)(
            currentFlowId.getOrElse(randomFlowId()),
            implicitly)
          logAlwaysSuccess()
          try {
            cb(
              Subscriptions.EventCallbackData(
                subscriptionEvent,
                streamId,
                request,
                currentFlowId
              ))
            logCallback()
          } catch {
            case NonFatal(e) => logCallbackFailure(e)
          }
        case Subscriptions.EventCallback.successPredicate(cb, _) =>
          logger.debug(s"$logDetails Executing callback")

          val predicate = try {
            val f = Option(
              cb(
                Subscriptions.EventCallbackData(
                  subscriptionEvent,
                  streamId,
                  request,
                  currentFlowId
                )))
            logCallback()
            f
          } catch {
            case NonFatal(e) =>
              logCallbackFailure(e)
              None
          }

          predicate match {
            case Some(true) =>
              commitCursors(subscriptionId, SubscriptionCursor(List(subscriptionEvent.cursor)), streamId)(
                currentFlowId.getOrElse(randomFlowId()),
                implicitly)
              logPredicateTrue()
            case Some(false) =>
              logPredicateFalse()
            case _ =>
          }

        case Subscriptions.EventCallback.successPredicateFuture(cb, _) =>
          val eventualPredicate = try {
            val f = Option(
              cb(
                Subscriptions.EventCallbackData(
                  subscriptionEvent,
                  streamId,
                  request,
                  currentFlowId
                )))
            logCallback()
            f
          } catch {
            case NonFatal(e) =>
              logCallbackFailure(e)
              None
          }

          eventualPredicate match {
            case Some(predicate) =>
              predicate.onComplete {
                case util.Success(true) =>
                  commitCursors(subscriptionId, SubscriptionCursor(List(subscriptionEvent.cursor)), streamId)(
                    currentFlowId.getOrElse(randomFlowId()),
                    implicitly)
                  logPredicateTrue()
                case util.Success(false) =>
                  logPredicateFalse()
                case util.Failure(e) =>
                  logPredicateFailure(e)
              }
            case None =>
          }
      }
    }

    for {
      nakadiSource <- eventsStreamedSource(subscriptionId, connectionClosedCallback, streamConfig)
    } yield {
      val finalGraph = sourceWithAdjustments(nakadiSource.source).toMat(Sink.foreach { subscriptionEvent =>
        handleSubscriptionEvent(nakadiSource.streamId, nakadiSource.request, subscriptionEvent)
      })(Keep.left)

      addStreamToKillSwitch(subscriptionId, nakadiSource.streamId, finalGraph.run())
      nakadiSource.streamId
    }
  }

  /**
    * Creates an event stream using [[eventsStreamed]] however also manages disconnects and reconnects from the server. Typically clients
    * want to use this as they don't need to handle these situations manually.
    *
    * This uses [[akka.pattern.after]] to recreate the streams in the case of server disconnects/no empty slots and cursor resets. The
    * timeouts respectively can be configured with [[HttpConfig.serverDisconnectRetryDelay]] and [[HttpConfig.noEmptySlotsCursorResetRetryDelay]].
    * The `connectionClosedCallback` parameter is still respected.
    *
    * NOTE: If the connection is closed by the client explicitly using the [[closeHttpConnection]] method then [[eventsStreamedManaged]] will not re-establish a connection.
    *
    * @param subscriptionId Id of subscription.
    * @param eventCallback The callback which gets executed every time an event is processed via the stream
    * @param connectionClosedCallback The callback which gets executed when the connection is closed, you typically want to reopen the subscription. NOTE: Don't use the parameter to reconnect the stream, this is automatically handled
    * @param streamConfig Configuration for the stream
    * @param eventStreamSupervisionDecider The supervision decider which decides what to do when an error is thrown in the stream. Default behaviour is if its a JSON Circe decoding exception on event data, it will commit the cursor, log the error and resume the stream, otherwise it will log the error and restart the stream.
    * @param modifySourceFunction Allows you to specify a function which modifies the underlying stream
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @tparam T
    * @return Initial Stream Id
    */
  def eventsStreamedManaged[T](subscriptionId: SubscriptionId,
                               eventCallback: Subscriptions.EventCallback[T],
                               connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                                 Subscriptions.ConnectionClosedCallback { _ =>
                                   ()
                                 },
                               streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig(),
                               modifySourceFunction: Option[(Source[SubscriptionEvent[T], UniqueKillSwitch]) => (
                                 Source[SubscriptionEvent[T],
                                        UniqueKillSwitch])] = None)(
      implicit decoder: Decoder[List[Event[T]]],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider
  ): Future[StreamId] = {
    eventsStreamed[T](
      subscriptionId,
      eventCallback,
      Subscriptions.ConnectionClosedCallback { connectionClosedData =>
        logger.info(s"Server disconnected Nakadi stream, reconnecting in ${kanadiHttpConfig.serverDisconnectRetryDelay
          .toString()} Old StreamId: ${connectionClosedData.oldStreamId}, SubscriptionId: ${subscriptionId.id.toString}")
        if (!connectionClosedData.cancelledByClient) {
          akka.pattern.after(kanadiHttpConfig.serverDisconnectRetryDelay, http.system.scheduler)(
            eventsStreamedManaged[T](
              subscriptionId,
              eventCallback,
              connectionClosedCallback,
              streamConfig,
              modifySourceFunction
            ))
        }
        // Executing the original callback if specified
        connectionClosedCallback.connectionClosedCallback(connectionClosedData)
      },
      streamConfig
    ).map { streamId =>
        logger.info(
          s"Initialized Nakadi Stream with StreamId: ${streamId.id}, SubscriptionId: ${subscriptionId.id.toString}")
        streamId
      }
      .recoverWith {
        case Subscriptions.Errors.NoEmptySlotsOrCursorReset(_) =>
          logger.info(
            s"No empty slots/cursor reset, reconnecting in ${kanadiHttpConfig.noEmptySlotsCursorResetRetryDelay
              .toString()}, SubscriptionId: ${subscriptionId.id.toString}")
          akka.pattern.after(kanadiHttpConfig.noEmptySlotsCursorResetRetryDelay, http.system.scheduler)(
            eventsStreamedManaged[T](
              subscriptionId,
              eventCallback,
              connectionClosedCallback,
              streamConfig,
              modifySourceFunction
            ))
      }
  }

  def eventsStreamedSourceManaged[T](subscriptionId: SubscriptionId,
                                     connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                                       Subscriptions.ConnectionClosedCallback { _ =>
                                         ()
                                       },
                                     streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig())(
      implicit
      decoder: Decoder[List[Event[T]]],
      flowId: FlowId,
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider)
    : Future[Subscriptions.NakadiSource[T]] = {
    eventsStreamedSource[T](
      subscriptionId,
      connectionClosedCallback,
      streamConfig
    ).map { nakadiSource =>
        logger.info(
          s"Initialized Nakadi Stream with StreamId: ${nakadiSource.streamId}, SubscriptionId: ${subscriptionId.id.toString}")
        nakadiSource
      }
      .recoverWith {
        case Subscriptions.Errors.NoEmptySlotsOrCursorReset(_) =>
          logger.info(
            s"No empty slots/cursor reset, reconnecting in ${kanadiHttpConfig.noEmptySlotsCursorResetRetryDelay
              .toString()}, SubscriptionId: ${subscriptionId.id.toString}")
          akka.pattern.after(kanadiHttpConfig.noEmptySlotsCursorResetRetryDelay, http.system.scheduler)(
            eventsStreamedSourceManaged[T](
              subscriptionId,
              connectionClosedCallback,
              streamConfig
            ))
      }
  }

  /**
    * Exception that is passed if the stream is cancelled by the client
    */
  case class CancelledByClient(subscriptionId: SubscriptionId, streamId: StreamId) extends Exception {
    override def getMessage =
      s"Stream cancelled by client SubscriptionId: ${subscriptionId.id}, StreamId: ${streamId.id}"
  }

  /**
    * Closes the underlying http connection for a subscriptionId/streamId.
    * @param subscriptionId
    * @param streamId
    * @return `true` If there was a reference to a connection (i.e. there was a stream running) or `false` if there was
    *        no reference. Note that cancelling the http connection will execute the [[eventsStreamed#connectionClosedCallback]]
    */
  def closeHttpConnection(subscriptionId: SubscriptionId, streamId: StreamId): Boolean = {
    killSwitches.get((subscriptionId, streamId)) match {
      case Some(killSwitch) =>
        killSwitch.abort(CancelledByClient(subscriptionId, streamId))
        true
      case None =>
        false
    }
  }

  /**
    * exposes statistics of specified subscription
    * @param subscriptionId Id of subscription
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def stats(subscriptionId: SubscriptionId)(
      implicit flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[SubscriptionStats]] = {
    val uri = baseUri_
      .withPath(baseUri_.path / "subscriptions" / subscriptionId.id.toString / "stats")

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders(flowId))
                  case Some(futureProvider) =>
                    futureProvider.provider().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders(flowId)
                    }
                }
      request  = HttpRequest(HttpMethods.GET, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request).map(decodeCompressed)
      result <- {
        if (response.status == StatusCodes.NotFound) {
          // TODO: Replace with response.discardEntityBytes once this is resolved: https://github.com/akka/akka-http/issues/1459
          response.entity.dataBytes.runWith(Sink.ignore)
          Future.successful(None)
        } else if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[SubscriptionStats]
            .map(x => Some(x))
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

}
