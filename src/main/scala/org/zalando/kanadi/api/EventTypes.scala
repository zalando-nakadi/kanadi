package org.zalando.kanadi.api

import java.net.URI
import java.time.OffsetDateTime

import defaults._
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import enumeratum._
import io.circe._
import io.circe.java8.time._
import io.circe.syntax._
import org.mdedetrich.webmodels.{FlowId, OAuth2TokenProvider}
import org.mdedetrich.webmodels.RequestHeaders.`X-Flow-ID`
import org.zalando.kanadi.api.defaults._
import org.zalando.kanadi.models._

import scala.concurrent.{ExecutionContext, Future}

sealed abstract class Audience(val id: String) extends EnumEntry with Product with Serializable {
  override val entryName = id
}

object Audience extends Enum[Audience] {
  val values = findValues
  case object BusinessUnitInternal extends Audience("business-unit-internal")
  case object CompanyInternal      extends Audience("company-internal")
  case object ComponentInternal    extends Audience("component-internal")
  case object ExternalPartner      extends Audience("external-partner")
  case object ExternalPublic       extends Audience("external-public")

  implicit val audienceEncoder: Encoder[Audience] =
    enumeratum.Circe.encoder(Audience)

  implicit val audienceDecoder: Decoder[Audience] =
    enumeratum.Circe.decoder(Audience)
}

sealed abstract class Category(val id: String) extends EnumEntry with Product with Serializable {
  override val entryName = id
}

object Category extends Enum[Category] {
  val values = findValues
  case object Business  extends Category("business")
  case object Data      extends Category("data")
  case object Undefined extends Category("undefined")

  implicit val categoryEncoder: Encoder[Category] =
    enumeratum.Circe.encoder(Category)

  implicit val categoryDecoder: Decoder[Category] =
    enumeratum.Circe.decoder(Category)
}

sealed abstract class EnrichmentStrategy(val id: String) extends EnumEntry with Product with Serializable {
  override val entryName = id
}

object EnrichmentStrategy extends Enum[EnrichmentStrategy] {
  val values = findValues
  case object MetadataEnrichment extends EnrichmentStrategy("metadata_enrichment")

  implicit val enrichmentStrategyEncoder: Encoder[EnrichmentStrategy] =
    enumeratum.Circe.encoder(EnrichmentStrategy)
  implicit val enrichmentStrategyDecoder: Decoder[EnrichmentStrategy] =
    enumeratum.Circe.decoder(EnrichmentStrategy)
}

sealed abstract class PartitionStrategy(val id: String) extends EnumEntry with Product with Serializable {
  override val entryName = id
}

object PartitionStrategy extends Enum[PartitionStrategy] {
  val values = findValues
  case object Random      extends PartitionStrategy("random")
  case object UserDefined extends PartitionStrategy("user_defined")
  case object Hash        extends PartitionStrategy("hash")

  implicit val partitionStrategyEncoder: Encoder[PartitionStrategy] =
    enumeratum.Circe.encoder(PartitionStrategy)
  implicit val partitionStrategyDecoder: Decoder[PartitionStrategy] =
    enumeratum.Circe.decoder(PartitionStrategy)
}

sealed abstract class CleanupPolicy(val id: String) extends EnumEntry with Product with Serializable {
  override def entryName = id
}

object CleanupPolicy extends Enum[CleanupPolicy] {
  val values = findValues
  case object Compact extends CleanupPolicy("compact")
  case object Delete  extends CleanupPolicy("delete")

  implicit val cleanupPolicyEncoder: Encoder[CleanupPolicy] =
    enumeratum.Circe.encoder(CleanupPolicy)
  implicit val cleanupPolicyDecoder: Decoder[CleanupPolicy] =
    enumeratum.Circe.decoder(CleanupPolicy)
}

sealed abstract class CompatibilityMode(val id: String) extends EnumEntry with Product with Serializable {
  override val entryName = id
}

object CompatibilityMode extends Enum[CompatibilityMode] {
  val values = findValues
  case object Compatible extends CompatibilityMode("compatible")
  case object Forward    extends CompatibilityMode("forward")
  case object None       extends CompatibilityMode("none")

  implicit val compatibilityModeEncoder: Encoder[CompatibilityMode] =
    enumeratum.Circe.encoder(CompatibilityMode)

  implicit val compatibilityModeDecoder: Decoder[CompatibilityMode] =
    enumeratum.Circe.decoder(CompatibilityMode)
}

final case class EventTypeSchema(version: Option[String],
                                 createdAt: Option[OffsetDateTime],
                                 `type`: EventTypeSchema.Type,
                                 schema: Json)

object EventTypeSchema {

  val anyJsonObject = EventTypeSchema(
    None,
    None,
    EventTypeSchema.Type.JsonSchema,
    JsonObject.singleton("type", "object".asJson).asJson.noSpaces.asJson
  )

  sealed abstract class Type(val id: String) extends EnumEntry with Product with Serializable {
    override val entryName = id
  }

  object Type extends Enum[Type] {
    val values = findValues

    case object JsonSchema extends Type("json_schema")

    implicit val eventTypeSchemaTypeEncoder: Encoder[Type] =
      enumeratum.Circe.encoder(Type)
    implicit val eventTypeSchemaTypeDecoder: Decoder[Type] =
      enumeratum.Circe.decoder(Type)
  }

  implicit val eventTypeSchemaEncoder: Encoder[EventTypeSchema] =
    Encoder.forProduct4("version", "created_at", "type", "schema")(x => EventTypeSchema.unapply(x).get)

  implicit val eventTypeSchemaDecoder: Decoder[EventTypeSchema] =
    Decoder.forProduct4("version", "created_at", "type", "schema")(EventTypeSchema.apply)
}

final case class EventTypeStatistics(messagesPerMinute: Int,
                                     messageSize: Int,
                                     readParallelism: Int,
                                     writeParallelism: Int)

object EventTypeStatistics {
  implicit val eventTypeStatisticsEncoder: Encoder[EventTypeStatistics] =
    Encoder.forProduct4(
      "messages_per_minute",
      "message_size",
      "read_parallelism",
      "write_parallelism"
    )(x => EventTypeStatistics.unapply(x).get)

  implicit val eventTypeStatisticsDecoder: Decoder[EventTypeStatistics] =
    Decoder.forProduct4(
      "messages_per_minute",
      "message_size",
      "read_parallelism",
      "write_parallelism"
    )(EventTypeStatistics.apply)
}

final case class AuthorizationAttribute(dataType: String, value: String)

object AuthorizationAttribute {
  implicit val eventTypeAuthorizationAuthorizationAttributeEncoder: Encoder[AuthorizationAttribute] =
    Encoder.forProduct2(
      "data_type",
      "value"
    )(x => AuthorizationAttribute.unapply(x).get)

  implicit val eventTypeAuthorizationAuthorizationAttributeDecoder: Decoder[AuthorizationAttribute] =
    Decoder.forProduct2(
      "data_type",
      "value"
    )(AuthorizationAttribute.apply)
}

final case class EventTypeAuthorization(admins: List[AuthorizationAttribute],
                                        readers: List[AuthorizationAttribute],
                                        writers: List[AuthorizationAttribute])

object EventTypeAuthorization {
  implicit val eventTypeAuthorizationEncoder: Encoder[EventTypeAuthorization] =
    Encoder.forProduct3(
      "admins",
      "readers",
      "writers"
    )(x => EventTypeAuthorization.unapply(x).get)

  implicit val eventTypeAuthorizationDecoder: Decoder[EventTypeAuthorization] =
    Decoder.forProduct3(
      "admins",
      "readers",
      "writers"
    )(EventTypeAuthorization.apply)
}

final case class EventTypeOptions(retentionTime: Int)

object EventTypeOptions {
  implicit val eventTypeOptionsEncoder: Encoder[EventTypeOptions] =
    Encoder.forProduct1(
      "retention_time"
    )(x => EventTypeOptions.unapply(x).get)

  implicit val eventTypeOptionsDecoder: Decoder[EventTypeOptions] =
    Decoder.forProduct1(
      "retention_time"
    )(EventTypeOptions.apply)
}

/**
  *
  * @param name Name of this [[EventType]]. The name is constrained by a regular expression. Note: the name can encode the owner/responsible for this [[EventType]] and ideally should follow a common pattern that makes it easy to read and understand, but this level of structure is not enforced. For example a team name and data type can be used such as 'acme-team.price-change'.
  * @param owningApplication Indicator of the (Stups) Application owning this [[EventType]].
  * @param category Defines the category of this [[EventType]]. The value set will influence, if not set otherwise, the default set of validations, [[enrichmentStrategies]], and the effective schema for validation in the following way: - [[Category.Undefined]]: No predefined changes apply. The effective schema for the validation is exactly the same as the [[EventTypeSchema]]. - `data`: Events of this category will be [[org.zalando.kanadi.api.Event.DataChange]]. The effective schema during the validation contains [[org.zalando.kanadi.api.Metadata]], and adds fields [[org.zalando.kanadi.api.Event.DataChange.data]] and [[org.zalando.kanadi.api.Event.DataChange.dataType]]. The passed [[EventTypeSchema]] defines the schema of [[org.zalando.kanadi.api.Event.DataChange.data]]. - [[Category.Business]]: Events of this category will be [[org.zalando.kanadi.api.Event.Business]]. The effective schema for validation contains [[org.zalando.kanadi.api.Metadata]] and any additionally defined properties passed in the [[EventTypeSchema]] directly on top level of the [[org.zalando.kanadi.api.Event]]. If name conflicts arise, creation of this [[EventType]] will be rejected.
  * @param enrichmentStrategies Determines the enrichment to be performed on an Event upon reception. Enrichment is performed once upon reception (and after validation) of an Event and is only possible on fields that are not defined on the incoming Event. For event types in categories 'business' or 'data' it's mandatory to use metadata_enrichment strategy. For 'undefined' event types it's not possible to use this strategy, since metadata field is not required. See documentation for the write operation for details on behaviour in case of unsuccessful enrichment.
  * @param partitionStrategy Determines how the assignment of the event to a partition should be handled. For details of possible values, see [[org.zalando.kanadi.api.Registry.partitionStrategies]].
  * @param compatibilityMode Compatibility mode provides a mean for event owners to evolve their schema, given changes respect the semantics defined by this field. It's designed to be flexible enough so that producers can evolve their schemas while not inadvertently breaking existent consumers. Once defined, the compatibility mode is fixed, since otherwise it would break a predefined contract, declared by the producer. List of compatibility modes: - [[CompatibilityMode.Compatible]]: Consumers can reliably parse events produced under different versions. Every event published since the first version is still valid based on the newest schema. When in compatible mode, it's allowed to add new optional properties and definitions to an existing schema, but no other changes are allowed. Under this mode, the following [[org.zalando.kanadi.api.EventTypeSchema.Type.JsonSchema]] attributes are not supported: `not`, `patternProperties`, `additionalProperties` and `additionalItems`. When validating events, additional properties is `false`. - [[CompatibilityMode.Forward]]: Compatible schema changes are allowed. It's possible to use the full json schema specification for defining schemas. Consumers of forward compatible event types can safely read events tagged with the latest schema version as long as they follow the robustness principle. - [[CompatibilityMode.None]]: Any schema modification is accepted, even if it might break existing producers or consumers. When validating events, no additional properties are accepted unless explicitly stated in the schema.
  * @param schema The most recent schema for this EventType. Submitted events will be validated against it.
  * @param partitionKeyFields Required when [[partitionStrategy]] is set to [[org.zalando.kanadi.api.PartitionStrategy.Hash]]. Must be absent otherwise. Indicates the fields used for evaluation the partition of Events of this type. If set it MUST be a valid required field as defined in the schema.
  * @param cleanupPolicy Event Type Cleanup Policy. 'delete' will delete old events after retention time expires. 'compact' will keep only the latest event for each event key. The key that will be used as a compaction key should be specified in [[PartitionCompactionKey]].
  * @param defaultStatistic Operational statistics for an [[EventType]]. This data MUST be provided by users on Event Type creation. Nakadi uses this object in order to provide an optimal number of partitions from a throughput perspective.
  * @param options Additional parameters for tuning internal behavior of Nakadi.
  * @param authorization Authorization section for an event type. This section defines three access control lists: one for producing events [[EventTypeAuthorization.writers]], one for consuming events [[EventTypeAuthorization.readers]], and one for administering an event type [[EventTypeAuthorization.admins]]. Regardless of the values of the authorization properties, administrator accounts will always be authorized.
  * @param writeScopes This field is used for event publishing access control. Nakadi only authorises publishers whose session contains at least one of the scopes in this list. If no scopes provided then anyone can publish to this event type.
  * @param readScopes This field is used for event consuming access control. Nakadi only authorises consumers whose session contains at least one of the scopes in this list. If no scopes provided then anyone can consume from this event type.
  * @param audience Intended target audience of the event type.
  * @param orderingKeyFields This is an optional field which can be useful in case the producer wants to communicate the complete order across all the events published to all the partitions.
  * @param orderingInstanceIds Indicates which field represents the data instance identifier and scope in which ordering_key_fields provides a strict order.
  * @param createdAt Date and time when this event type was created.
  * @param updatedAt Date and time when this event type was last updated.
  */
final case class EventType(
    name: EventTypeName,
    owningApplication: String,
    category: Category,
    enrichmentStrategies: List[EnrichmentStrategy] = List(EnrichmentStrategy.MetadataEnrichment),
    partitionStrategy: Option[PartitionStrategy] = None,
    compatibilityMode: Option[CompatibilityMode] = None,
    schema: EventTypeSchema = EventTypeSchema.anyJsonObject,
    partitionKeyFields: Option[List[String]] = None,
    cleanupPolicy: Option[CleanupPolicy] = None,
    defaultStatistic: Option[EventTypeStatistics] = None,
    options: Option[EventTypeOptions] = None,
    authorization: Option[EventTypeAuthorization] = None,
    writeScopes: Option[List[WriteScope]] = None,
    readScopes: Option[List[ReadScope]] = None,
    audience: Option[Audience] = None,
    orderingKeyFields: Option[List[String]] = None,
    orderingInstanceIds: Option[List[String]] = None,
    createdAt: Option[OffsetDateTime] = None,
    updatedAt: Option[OffsetDateTime] = None
)

object EventType {
  implicit val eventTypeEncoder: Encoder[EventType] = Encoder.forProduct19(
    "name",
    "owning_application",
    "category",
    "enrichment_strategies",
    "partition_strategy",
    "compatibility_mode",
    "schema",
    "partition_key_fields",
    "cleanup_policy",
    "default_statistic",
    "options",
    "authorization",
    "write_scopes",
    "read_scopes",
    "audience",
    "ordering_key_fields",
    "ordering_instance_ids",
    "created_at",
    "updated_at"
  )(x => EventType.unapply(x).get)

  implicit val eventTypeDecoder: Decoder[EventType] = Decoder.forProduct19(
    "name",
    "owning_application",
    "category",
    "enrichment_strategies",
    "partition_strategy",
    "compatibility_mode",
    "schema",
    "partition_key_fields",
    "cleanup_policy",
    "default_statistic",
    "options",
    "authorization",
    "write_scopes",
    "read_scopes",
    "audience",
    "ordering_key_fields",
    "ordering_instance_ids",
    "created_at",
    "updated_at"
  )(EventType.apply)
}

case class EventTypes(baseUri: URI, oAuth2TokenProvider: Option[OAuth2TokenProvider] = None)(
    implicit
    kanadiHttpConfig: HttpConfig,
    http: HttpExt,
    materializer: Materializer)
    extends EventTypesInterface {
  protected val logger: LoggerTakingImplicit[FlowId] = Logger.takingImplicit[FlowId](classOf[EventTypes])

  private val baseUri_ = Uri(baseUri.toString)

  /**
    * Returns a list of all registered [[EventType]]
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def list()(implicit flowId: FlowId = randomFlowId(), executionContext: ExecutionContext): Future[List[EventType]] = {
    val uri = baseUri_.withPath(baseUri_.path / "event-types")

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders)
                  case Some(futureProvider) =>
                    futureProvider.value().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders
                    }
                }
      request  = HttpRequest(HttpMethods.GET, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request)
      result <- {
        if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[List[EventType]]
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Creates a new [[EventType]].
    *
    * The fields enrichment-strategies and partition-resolution-strategy have all an effect on the incoming [[org.zalando.kanadi.api.Event]] of this [[EventType]]. For its impacts on the reception of events please consult the Event submission API methods.
    * Validation strategies define an array of validation stategies to be evaluated on reception of an Event of this [[EventType]]. Details of usage can be found in this external document http://zalando.github.io/nakadi-manual/
    * Enrichment strategy. (todo: define this part of the API).
    * The schema of an [[EventType]] is defined as an [[EventTypeSchema]]. Currently only the value [[EventTypeSchema.Type.JsonSchema]] is supported, representing JSON Schema draft 04.
    *
    * Following conditions are enforced. Not meeting them will fail the request with the indicated status (details are provided in the Problem object):
    *
    * EventType name on creation must be unique (or attempting to update an [[EventType]] with this method), otherwise the request is rejected with status 409 Conflict.
    * Using [[EventTypeSchema.Type]] other than [[EventTypeSchema.Type.JsonSchema]] or passing a [[EventTypeSchema.schema]] that is invalid with respect to the schema's type. Rejects with 422 Unprocessable entity.
    * Referring any Enrichment or Partition strategies that do not exist or whose parametrization is deemed invalid. Rejects with 422 Unprocessable entity.
    *
    * Nakadi MIGHT impose necessary schema, validation and enrichment minimal configurations that MUST be followed by all EventTypes (examples include: validation rules to match the schema; enriching every Event with the reception date-type; adhering to a set of schema fields that are mandatory for all EventTypes). The mechanism to set and inspect such rules is not defined at this time and might not be exposed in the API.
    *
    * @param eventType
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def create(eventType: EventType)(implicit flowId: FlowId = randomFlowId(),
                                   executionContext: ExecutionContext): Future[Unit] = {
    val uri = baseUri_.withPath(baseUri_.path / "event-types")

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders)
                  case Some(futureProvider) =>
                    futureProvider.value().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders
                    }
                }
      entity   <- Marshal(eventType).to[RequestEntity]
      request  = HttpRequest(HttpMethods.POST, uri, headers, entity)
      response <- http.singleRequest(request)
      result <- {
        if (response.status.isSuccess()) {
          response.discardEntityBytes()
          Future.successful(())
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Returns the [[EventType]] identified by its name.
    * @param name Name of the EventType to load.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def get(name: EventTypeName)(implicit flowId: FlowId = randomFlowId(),
                               executionContext: ExecutionContext): Future[Option[EventType]] = {
    val uri =
      baseUri_.withPath(baseUri_.path / "event-types" / name.name)

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders)
                  case Some(futureProvider) =>
                    futureProvider.value().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders
                    }
                }
      request  = HttpRequest(HttpMethods.GET, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request)
      result <- {
        if (response.status == StatusCodes.NotFound) {
          response.discardEntityBytes()
          Future.successful(None)
        } else if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[EventType]
            .map(Some.apply)
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Updates the [[EventType]] identified by its name. Behaviour is the same as creation of [[EventType]] (See [[create]]) except where noted below.
    * @param eventType EventType to be updated.
    * @param name Name of the EventType to update.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def update(name: EventTypeName, eventType: EventType)(implicit flowId: FlowId = randomFlowId(),
                                                        executionContext: ExecutionContext): Future[Unit] = {
    val uri =
      baseUri_.withPath(baseUri_.path / "event-types" / name.name)

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders)
                  case Some(futureProvider) =>
                    futureProvider.value().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders
                    }
                }
      entity   <- Marshal(eventType).to[RequestEntity]
      request  = HttpRequest(HttpMethods.PUT, uri, headers, entity)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request)
      result <- {
        if (response.status.isSuccess()) {
          response.discardEntityBytes()
          Future.successful(())
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

  /**
    * Deletes an [[EventType]] identified by its name. All events in the [[EventType]]'s stream' will also be removed. Note: deletion happens asynchronously, which has the following consequences:
    *
    * Creation of an equally named EventType before the underlying topic deletion is complete might not succeed (failure is a 409 Conflict).
    * Events in the stream may be visible for a short period of time before being removed.
    *
    * @param name Name of the EventType to delete.
    * @param flowId The flow id of the request, which is written into the logs and passed to called services. Helpful for operational troubleshooting and log analysis.
    * @return
    */
  def delete(name: EventTypeName)(implicit flowId: FlowId = randomFlowId(),
                                  executionContext: ExecutionContext): Future[Unit] = {
    val uri =
      baseUri_.withPath(baseUri_.path / "event-types" / name.name)

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    for {
      headers <- oAuth2TokenProvider match {
                  case None => Future.successful(baseHeaders)
                  case Some(futureProvider) =>
                    futureProvider.value().map { oAuth2Token =>
                      toHeader(oAuth2Token) +: baseHeaders
                    }
                }
      request  = HttpRequest(HttpMethods.DELETE, uri, headers)
      _        = logger.debug(request.toString)
      response <- http.singleRequest(request)
      result <- {
        if (response.status.isSuccess()) {
          response.discardEntityBytes()
          Future.successful(())
        } else {
          processNotSuccessful(response)
        }
      }
    } yield result
  }

}
