package org.zalando.kanadi
package api

import java.util.UUID
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.models.{EventId, EventTypeName, PublishedBy, SpanCtx}

import java.time.OffsetDateTime

class JsonSpec extends AnyFreeSpec with Matchers with EitherValues {

  val uuid      = UUID.randomUUID()
  val testEvent = SomeEvent("Bart", "Simpson", uuid)
  val now       = OffsetDateTime.now()
  val md = Metadata(eid = EventId(UUID.fromString("4ae5011e-eb01-11e5-8b4a-1c6f65464fc6")),
                    occurredAt = now,
                    publishedBy = Some(PublishedBy("bart_simpson")))

  val coreEventJson = s"""
    "first_name": "Bart",
    "last_name": "Simpson",
    "uuid": "${uuid.toString}"
  """

  val metadata =
    s""""eid": "4ae5011e-eb01-11e5-8b4a-1c6f65464fc6", "occurred_at": ${now.asJson}, "published_by": "bart_simpson""""

  val businessEventJson = s"""{
    "metadata": {$metadata},
    $coreEventJson
  }"""

  val dataEventJson = s"""{
    "metadata": {$metadata},
    "data_op": "C",
    "data": {$coreEventJson},
    "data_type": "blah"
  }"""

  val undefinedEventJson = s"{$coreEventJson}"

  val eventTypeWithAnnotationsJson =
    """{
      |  "name" : "order.order_cancelled",
      |  "owning_application" : "price-service",
      |  "category" : "undefined",
      |  "enrichment_strategies" : [
      |    "metadata_enrichment"
      |  ],
      |  "schema" : {
      |    "type" : "json_schema",
      |    "schema" : "{\"type\":\"object\"}"
      |  },
      |  "annotations" : {
      |    "nakadi.io/internal-event-type" : "true",
      |    "criticality" : "low"
      |  }
      |}""".stripMargin

  "Parse business events" in {
    decode[Event[SomeEvent]](businessEventJson).value mustEqual Event.Business(testEvent, md)
  }

  "Parse data events" in {
    decode[Event[SomeEvent]](dataEventJson).value mustEqual Event.DataChange(testEvent,
                                                                             "blah",
                                                                             DataOperation.Create,
                                                                             md)
  }

  "Parse undefined events" in {
    decode[Event[SomeEvent]](undefinedEventJson).value mustEqual Event.Undefined(testEvent)
  }

  // Sample data is taken from official Nakadi source at https://github.com/zalando/nakadi/blob/effb2ed7e95bd329ab73ce06b2857aa57510e539/src/test/java/org/zalando/nakadi/validation/JSONSchemaValidationTest.java

  val spanCtxJson =
    """{"eid":"04ba01db-9990-44bd-b733-be69008c5da3","occurred_at":"1992-08-03T10:00:00Z","span_ctx":{"ot-tracer-spanid":"b268f901d5f2b865","ot-tracer-traceid":"e9435c17dabe8238","ot-baggage-foo":"bar"}}"""

  val spanCtxBadJson =
    """{"eid":"04ba01db-9990-44bd-b733-be69008c5da3","occurred_at":"1992-08-03T10:00:00Z","span_ctx":{"ot-tracer-spanid":"b268f901d5f2b865","ot-tracer-traceid":42,"ot-baggage-foo":"bar"}}"""

  val spanCtxEventMetadata = Metadata(
    eid = EventId(UUID.fromString("04ba01db-9990-44bd-b733-be69008c5da3")),
    occurredAt = OffsetDateTime.parse("1992-08-03T10:00:00Z"),
    spanCtx = Some(
      SpanCtx(
        Map(
          "ot-tracer-spanid"  -> "b268f901d5f2b865",
          "ot-tracer-traceid" -> "e9435c17dabe8238",
          "ot-baggage-foo"    -> "bar"
        )))
  )

  val eventTypeWithAnnotationsData = EventType(
    name = EventTypeName("order.order_cancelled"),
    owningApplication = "price-service",
    category = Category.Undefined,
    enrichmentStrategies = List(EnrichmentStrategy.MetadataEnrichment),
    schema = EventTypeSchema.anyJsonObject,
    annotations = Some(Map("nakadi.io/internal-event-type" -> "true", "criticality" -> "low"))
  )

  "SpanCtx decoding example" in {
    decode[Metadata](spanCtxJson).value mustEqual spanCtxEventMetadata
  }

  "SpanCtx encoding example" in {
    spanCtxEventMetadata.asJson.printWith(Printer.noSpaces.copy(dropNullValues = true)) mustEqual spanCtxJson
  }

  "SpanCtx fail decoding example" in {
    decode[Metadata](spanCtxBadJson).isLeft mustEqual true
  }

  "Decoding EventType example" in {
    decode[EventType](eventTypeWithAnnotationsJson).value mustEqual eventTypeWithAnnotationsData
  }

  "Encoding EventType example" in {
    eventTypeWithAnnotationsData.asJson.printWith(
      Printer.spaces2.copy(dropNullValues = true)) mustEqual eventTypeWithAnnotationsJson
  }
}
