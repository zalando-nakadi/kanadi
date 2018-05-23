import java.util.UUID
import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import org.specs2.matcher.Matchers
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.java8.time._
import org.zalando.kanadi.api.Event
import org.zalando.kanadi.api.DataOperation
import org.zalando.kanadi.api.Metadata
import org.zalando.kanadi.models.EventId
import java.time.OffsetDateTime

class JsonSpec extends Specification {
  override def is: SpecStructure = sequential ^ s2"""
    Parse business events      $businessEvent
    Parse data events          $dataEvent
    Parse undefined events     $undefinedEvent
    """

  val uuid      = UUID.randomUUID()
  val testEvent = SomeEvent("Bart", "Simpson", uuid)
  val now       = OffsetDateTime.now()
  val md        = Metadata(eid = EventId("4ae5011e-eb01-11e5-8b4a-1c6f65464fc6"), occurredAt = now)

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

  def businessEvent = {
    decode[Event[SomeEvent]](businessEventJson) must beRight(Event.Business(testEvent, md))
  }

  def dataEvent = {
    decode[Event[SomeEvent]](dataEventJson) must beRight(Event.DataChange(testEvent, "blah", DataOperation.Create, md))
  }

  def undefinedEvent = {
    decode[Event[SomeEvent]](undefinedEventJson) must beRight(Event.Undefined(testEvent))
  }
}
