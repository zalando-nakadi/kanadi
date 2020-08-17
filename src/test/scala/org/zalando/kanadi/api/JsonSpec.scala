package org.zalando.kanadi
package api

import java.util.UUID

import cats.syntax.either._
import cats.instances.either._
import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.zalando.kanadi.models.{EventId, SpanCtx}
import java.time.OffsetDateTime

import io.circe.CursorOp.DownField

class JsonSpec extends Specification {
  override def is: SpecStructure = s2"""
    Parse business events         $businessEvent
    Parse data events             $dataEvent
    Parse undefined events        $undefinedEvent
    SpanCtx decoding example      $decodeSpnCtx
    SpanCtx encoding example      $encodeSpnCtx
    SpanCtx fail decoding example $badDecodeSpnCtx
    """

  val uuid      = UUID.randomUUID()
  val testEvent = SomeEvent("Bart", "Simpson", uuid)
  val now       = OffsetDateTime.now()
  val md        = Metadata(eid = EventId(UUID.fromString("4ae5011e-eb01-11e5-8b4a-1c6f65464fc6")), occurredAt = now)

  val coreEventJson = s"""
    "first_name": "Bart",
    "last_name": "Simpson",
    "uuid": "${uuid.toString}"
  """

  val metadata =
    s""""eid": "4ae5011e-eb01-11e5-8b4a-1c6f65464fc6", "occurred_at": ${now.asJson}"""

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

  def businessEvent =
    decode[Event[SomeEvent]](businessEventJson) must beRight(Event.Business(testEvent, md))

  def dataEvent =
    decode[Event[SomeEvent]](dataEventJson) must beRight(Event.DataChange(testEvent, "blah", DataOperation.Create, md))

  def undefinedEvent =
    decode[Event[SomeEvent]](undefinedEventJson) must beRight(Event.Undefined(testEvent))

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

  def decodeSpnCtx =
    decode[Metadata](spanCtxJson) must beRight(spanCtxEventMetadata)

  def encodeSpnCtx =
    spanCtxEventMetadata.asJson.pretty(Printer.noSpaces.copy(dropNullValues = true)) mustEqual spanCtxJson

  def badDecodeSpnCtx =
    decode[Metadata](spanCtxBadJson) must beLeft(
      DecodingFailure("String", List(DownField("ot-tracer-traceid"), DownField("span_ctx"))))
}
