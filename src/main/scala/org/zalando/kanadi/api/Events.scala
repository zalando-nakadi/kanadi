package org.zalando.kanadi.api

import java.net.URI
import java.time.OffsetDateTime

import defaults._
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.syntax.either._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import enumeratum._
import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.mdedetrich.webmodels.{FlowId, OAuth2TokenProvider}
import org.mdedetrich.webmodels.RequestHeaders.`X-Flow-ID`
import org.mdedetrich.webmodels.circe._
import org.zalando.kanadi.models._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

sealed abstract class Event[T](val data: T) {
  def getMetadata: Option[Metadata] = this match {
    case e: Event.DataChange[_] => Some(e.metadata)
    case e: Event.Business[_]   => Some(e.metadata)
    case _: Event.Undefined[_]  => None
  }
}

object Event {
  final case class DataChange[T](override val data: T,
                                 dataType: String,
                                 dataOperation: DataOperation,
                                 metadata: Metadata)
      extends Event[T](data)

  object DataChange {
    implicit def eventDataChangeEncoder[T](implicit encoder: Encoder[T]): Encoder[DataChange[T]] =
      Encoder.forProduct4(
        "data",
        "data_type",
        "data_op",
        "metadata"
      )(x => DataChange.unapply(x).get)

    implicit def eventDataChangeDecoder[T](implicit decoder: Decoder[T]): Decoder[DataChange[T]] =
      Decoder.forProduct4(
        "data",
        "data_type",
        "data_op",
        "metadata"
      )(DataChange.apply)
  }

  final case class Business[T](override val data: T, metadata: Metadata = Metadata()) extends Event[T](data)

  object Business {
    implicit def eventBusinessEncoder[T](implicit encoder: Encoder[T]): Encoder[Business[T]] =
      Encoder.instance[Business[T]] { x =>
        val metadata = Json.obj(
          "metadata" -> x.metadata.asJson
        )
        val data = x.data.asJson
        data.deepMerge(metadata)
      }

    implicit def eventBusinessDecoder[T](
        implicit decoder: Decoder[T]
    ): Decoder[Business[T]] =
      Decoder.instance[Business[T]] { c =>
        for {
          metadata <- c.downField("metadata").as[Metadata]
          data     <- c.as[T]
        } yield Business(data, metadata)
      }
  }

  final case class Undefined[T](override val data: T) extends Event[T](data)

  object Undefined {
    implicit def eventUndefinedEncoder[T](implicit encoder: Encoder[T]): Encoder[Undefined[T]] =
      Encoder.instance[Undefined[T]] { x =>
        x.data.asJson
      }

    implicit def eventUndefinedDecoder[T](
        implicit decoder: Decoder[T]
    ): Decoder[Undefined[T]] =
      Decoder.instance[Undefined[T]] { c =>
        for {
          data <- c.as[T]
        } yield Undefined(data)
      }
  }

  implicit def eventEncoder[T](implicit encoder: Encoder[T]): Encoder[Event[T]] =
    Encoder.instance[Event[T]] {
      case e: Event.DataChange[T] => e.asJson
      case e: Event.Business[T]   => e.asJson
      case e: Event.Undefined[T]  => e.asJson
    }

  implicit def eventDecoder[T](implicit decoder: Decoder[T]): Decoder[Event[T]] =
    Decoder.instance[Event[T]](
      c => {
        val dataOpR   = c.downField("data_op").as[Option[String]]
        val metadataR = c.downField("metadata").as[Option[Metadata]]

        (for {
          dataOp   <- dataOpR
          metadata <- metadataR
        } yield {
          (dataOp, metadata) match {
            case (Some(_), Some(_)) =>
              c.as[Event.DataChange[T]]: Result[Event[T]]
            case (None, Some(_)) =>
              c.as[Event.Business[T]]: Result[Event[T]]
            case _ =>
              c.as[Event.Undefined[T]]: Result[Event[T]]
          }
        }).joinRight
      }
    )
}

sealed abstract class DataOperation(val id: String) extends EnumEntry with Product with Serializable {
  override val entryName = id
}

object DataOperation extends Enum[DataOperation] {
  val values = findValues
  final case object Create   extends DataOperation("C")
  final case object Update   extends DataOperation("U")
  final case object Delete   extends DataOperation("D")
  final case object Snapshot extends DataOperation("S")

  implicit val dataOperationEncoder: Encoder[DataOperation] =
    enumeratum.Circe.encoder(DataOperation)
  implicit val dataOperationDecoder: Decoder[DataOperation] =
    enumeratum.Circe.decoder(DataOperation)
}

final case class Metadata(eid: EventId = EventId.random,
                          occurredAt: OffsetDateTime = OffsetDateTime.now,
                          eventType: Option[EventTypeName] = None,
                          receivedAt: Option[OffsetDateTime] = None,
                          parentEids: Option[List[EventId]] = None,
                          flowId: Option[FlowId] = None,
                          partition: Option[Partition] = None,
                          partitionCompactionKey: Option[PartitionCompactionKey] = None,
                          spanCtx: Option[SpanCtx] = None)

object Metadata {

  implicit val metadataEncoder: Encoder[Metadata] = Encoder.forProduct9(
    "eid",
    "occurred_at",
    "event_type",
    "received_at",
    "parent_eids",
    "flow_id",
    "partition",
    "partition_compaction_key",
    "span_ctx"
  )(x => Metadata.unapply(x).get)

  implicit val metadataDecoder: Decoder[Metadata] = Decoder.forProduct9(
    "eid",
    "occurred_at",
    "event_type",
    "received_at",
    "parent_eids",
    "flow_id",
    "partition",
    "partition_compaction_key",
    "span_ctx"
  )(Metadata.apply)
}

object Events {
  final case class BatchItemResponse(eid: Option[EventId],
                                     publishingStatus: PublishingStatus,
                                     step: Option[Step],
                                     detail: Option[String])

  object BatchItemResponse {
    implicit val batchItemResponseEncoder: Encoder[BatchItemResponse] =
      Encoder.forProduct4(
        "eid",
        "publishing_status",
        "step",
        "detail"
      )(x => BatchItemResponse.unapply(x).get)

    implicit val batchItemResponseDecoder: Decoder[BatchItemResponse] =
      Decoder.forProduct4(
        "eid",
        "publishing_status",
        "step",
        "detail"
      )(BatchItemResponse.apply)
  }

  sealed abstract class PublishingStatus(val id: String) extends EnumEntry with Product with Serializable {
    override val entryName = id
  }

  object PublishingStatus extends Enum[PublishingStatus] {
    val values = findValues
    final case object Submitted extends PublishingStatus("submitted")
    final case object Failed    extends PublishingStatus("failed")
    final case object Aborted   extends PublishingStatus("aborted")

    implicit val eventsErrorsPublishingStatusEncoder: Encoder[PublishingStatus] =
      enumeratum.Circe.encoder(PublishingStatus)
    implicit val eventsErrorsPublishingStatusDecoder: Decoder[PublishingStatus] =
      enumeratum.Circe.decoder(PublishingStatus)
  }

  sealed abstract class Step(val id: String) extends EnumEntry with Product with Serializable {
    override val entryName = id
  }

  object Step extends Enum[Step] {
    val values = findValues
    final case object None         extends Step("none")
    final case object Validating   extends Step("validating")
    final case object Partitioning extends Step("partitioning")
    final case object Enriching    extends Step("enriching")
    final case object Publishing   extends Step("publishing")

    implicit val eventsErrorsStepEncoder: Encoder[Step] =
      enumeratum.Circe.encoder(Step)
    implicit val eventsErrorsStepDecoder: Decoder[Step] =
      enumeratum.Circe.decoder(Step)
  }

  sealed abstract class Errors extends Exception

  object Errors {
    final case class EventValidation(batchItemResponse: List[BatchItemResponse]) extends Errors {
      override def getMessage: String =
        s"Error publishing events, errors are ${batchItemResponse.mkString("\n")}"
    }
  }
}

case class Events(baseUri: URI, oAuth2TokenProvider: Option[OAuth2TokenProvider] = None)(
    implicit
    kanadiHttpConfig: HttpConfig,
    exponentialBackoffConfig: ExponentialBackoffConfig,
    http: HttpExt,
    materializer: Materializer)
    extends EventsInterface {
  private val baseUri_                               = Uri(baseUri.toString)
  protected val logger: LoggerTakingImplicit[FlowId] = Logger.takingImplicit[FlowId](classOf[Events])

  /**
    * Publishes a batch of [[Event]]'s of this [[org.zalando.kanadi.models.EventTypeName]]. All items must be of the EventType identified by name.
    *
    * Reception of Events will always respect the configuration of its [[org.zalando.kanadi.models.EventTypeName]] with respect to validation, enrichment and partition. The steps performed on reception of incoming message are:
    *
    * Every validation rule specified for the [[EventType]] will be checked in order against the incoming Events. Validation rules are evaluated in the order they are defined and the Event is rejected in the first case of failure. If the offending validation rule provides information about the violation it will be included in the BatchItemResponse. If the [[org.zalando.kanadi.models.EventTypeName]] defines schema validation it will be performed at this moment. The size of each Event will also be validated. The maximum size per Event is 999,000 bytes. We use the batch input to measure the size of events, so unnecessary spaces, tabs, and carriage returns will count towards the event size.
    * Once the validation succeeded, the content of the Event is updated according to the enrichment rules in the order the rules are defined in the [[EventType]]. No preexisting value might be changed (even if added by an enrichment rule). Violations on this will force the immediate rejection of the Event. The invalid overwrite attempt will be included in the item's BatchItemResponse object.
    * The incoming Event's relative ordering is evaluated according to the rule on the [[EventType]]. Failure to evaluate the rule will reject the Event.
    *
    * Given the batched nature of this operation, any violation on validation or failures on enrichment or partitioning will cause the whole batch to be rejected, i.e. none of its elements are pushed to the underlying broker.
    *
    * Failures on writing of specific partitions to the broker might influence other partitions. Failures at this stage will fail only the affected partitions.
    *
    * @param name Name of the EventType
    * @param events The Event being published
    * @param encoder
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @tparam T
    * @return
    */
  def publish[T](name: EventTypeName, events: List[Event[T]], fillMetadata: Boolean = true)(
      implicit encoder: Encoder[T],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Unit] =
    if (kanadiHttpConfig.failedPublishEventRetry) {
      publishWithRecover(name, events, List.empty, fillMetadata, exponentialBackoffConfig.initialDelay, count = 0)
    } else publishBase(name, events, fillMetadata)

  private[api] def publishWithRecover[T](name: EventTypeName,
                                         events: List[Event[T]],
                                         currentNotValidEvents: List[Events.BatchItemResponse],
                                         fillMetadata: Boolean,
                                         currentDuration: FiniteDuration,
                                         count: Int)(
      implicit encoder: Encoder[T],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Unit] = {
    def retryUnexpectedFailure(events: List[Event[T]],
                               count: Int,
                               e: Exception,
                               currentDuration: FiniteDuration): Future[Unit] = {
      val eventIds = events.flatMap(x => eventWithUndefinedEventIdFallback(x))
      if (count > exponentialBackoffConfig.maxRetries) {
        logger.error(
          s"Max retry failed for publishing events, event id's still not submitted are ${eventIds.map(_.id).mkString(",")}")
        Future.failed(e)
      } else {
        val newDuration = exponentialBackoffConfig.calculate(count, currentDuration)

        logger.warn(
          s"Events with eid's ${eventIds.map(_.id).mkString(",")} failed to submit, retrying in ${newDuration.toMillis} millis")

        akka.pattern.after(newDuration, http.system.scheduler)(
          publishWithRecover(name, events, currentNotValidEvents, fillMetadata, newDuration, count + 1))
      }
    }

    publishBase(name, events, fillMetadata).recoverWith {
      case Events.Errors.EventValidation(errors) =>
        if (count > exponentialBackoffConfig.maxRetries) {
          val finalEvents =
            (errors ++ currentNotValidEvents).filter(_.publishingStatus != Events.PublishingStatus.Submitted)
          logger.error(
            s"Max retry failed for publishing events, event id's still not submitted are ${finalEvents.flatMap(_.eid.map(_.id)).mkString(",")}")
          Future.failed(Events.Errors.EventValidation(finalEvents))
        } else {
          val (notValid, retry) = errors.partition(
            response =>
              response.step
                .contains(Events.Step.Validating) || response.publishingStatus == Events.PublishingStatus.Submitted)
          val toRetry = events.filter { event =>
            eventWithUndefinedEventIdFallback(event) match {
              case Some(eid) => !retry.exists(_.eid.contains(eid))
              case None      => false
            }
          }

          val newDuration = exponentialBackoffConfig.calculate(count, currentDuration)

          logger.warn(
            s"Events with eid's ${retry.flatMap(_.eid).map(_.id).mkString(",")} failed to submit, retrying in ${newDuration.toMillis} millis")

          val invalidSchemaEvents = notValid.filter(_.publishingStatus != Events.PublishingStatus.Submitted)

          if (invalidSchemaEvents.nonEmpty) {
            val errorDetails = invalidSchemaEvents
              .map { response =>
                val detail  = response.detail
                val eventId = response.eid.map(_.id)
                s"eid: ${eventId.getOrElse("N/A")}, detail: ${detail.getOrElse("N/A")}"
              }
              .mkString(",")
            logger.error(s"Events $errorDetails did not pass validation schema, not submitting")
          }

          val newNotValidEvents = (currentNotValidEvents ++ notValid).distinct

          akka.pattern.after(newDuration, http.system.scheduler)(
            publishWithRecover(name, toRetry, newNotValidEvents, fillMetadata, newDuration, count + 1))
        }
      case e: RuntimeException
          if e.getMessage.contains(
            "The http server closed the connection unexpectedly before delivering responses for") =>
        retryUnexpectedFailure(events, count, e, currentDuration)
      case httpServiceError: HttpServiceError
          if httpServiceError.httpResponse.status.intValue().toString.startsWith("5") =>
        retryUnexpectedFailure(events, count, httpServiceError, currentDuration)
    }
  }

  /**
    * If we have an event of type [[Event.Undefined]], this function will try and manually parse the event to see if
    * it has an "eid" field. The "eid" field is not mandatory in [[Event.Undefined]] however there is a chance it can
    * still be there.
    *
    * @param event
    * @param encoder
    * @tparam T
    * @return
    */
  private[api] def eventWithUndefinedEventIdFallback[T](event: Event[T])(
      implicit encoder: Encoder[T]): Option[EventId] =
    event.getMetadata.map(_.eid) orElse {
      (event.data.asJson \\ "eid").headOption.flatMap { json =>
        json.as[EventId].toOption
      }
    }

  private[api] def publishBase[T](name: EventTypeName, events: List[Event[T]], fillMetadata: Boolean = true)(
      implicit encoder: Encoder[T],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Unit] = {
    val uri =
      baseUri_.withPath(baseUri_.path / "event-types" / name.name / "events")

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    val finalEvents = if (fillMetadata) {
      events.map {
        case e: Event.Business[_] =>
          e.copy(metadata = e.metadata.copy(eventType = Some(e.metadata.eventType.getOrElse(name))))
        case e: Event.DataChange[_] =>
          e.copy(metadata = e.metadata.copy(eventType = Some(e.metadata.eventType.getOrElse(name))))
        case e: Event.Undefined[_] => e
      }
    } else events

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders)
                  case Some(futureProvider) =>
                    futureProvider.value().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders
                    }
                }

      entity   <- Marshal(finalEvents).to[RequestEntity]
      request  = HttpRequest(HttpMethods.POST, uri, headers, entity)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request)
      result <- {
        response.status match {
          case StatusCodes.UnprocessableEntity | StatusCodes.MultiStatus =>
            Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
              .to[List[Events.BatchItemResponse]]
              .map(x => throw Events.Errors.EventValidation(x))
          case s if s.isSuccess() =>
            response.discardEntityBytes()
            Future.successful(())
          case _ =>
            processNotSuccessful(request, response)
        }
      }
    } yield result
  }
}
