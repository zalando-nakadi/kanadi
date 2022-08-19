package org.zalando.kanadi.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.zalando.kanadi.{Config, Utils}
import org.zalando.kanadi.models.{EventTypeName, StreamId}
import org.zalando.kanadi.api.EventTypeSchema.Type

import java.util.UUID
import io.circe.syntax._
import org.mdedetrich.webmodels.FlowId
import org.specs2.specification.{AfterAll, BeforeAll}
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.api.Event.{AvroEvent, eventDecoder}

import java.net.URI
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.DurationInt
import scala.util.Success

case class AvroRecordTest(foo: String, bar: Int)

class AvroBasicSpec(implicit ec: ExecutionEnv)
    extends Specification
    with FutureMatchers
    with Config
    with BeforeAll
    with AfterAll {

  override val config = ConfigFactory.load()

  override lazy val nakadiUri = new URI("http://localhost:8080")

  override def is: SpecStructure = sequential ^
    s2"""
    Publish avro events $publishAvroEvent
    Consume published avro evetns $consumeAvroEvent
   """

  implicit val system = ActorSystem()
  implicit val http   = Http()

  val eventTypeName     = EventTypeName(s"Kanadi-Test-Event-${UUID.randomUUID().toString}")
  val OwningApplication = "KANADI"
  val schema = """{
    |"name": "AvroTestRecord",
    |"namespace": "com.zalando.test",
    |"type": "record",
    |"fields": [
    |{
    | "name": "foo",
    |  "type": "string"
    |},
    |{
    |  "name": "bar",
    |  "type": "int"
    |}
    |]
    |}""".stripMargin('|')

  val eventType = EventType(eventTypeName,
                            OwningApplication,
                            Category.Business,
                            schema = EventTypeSchema(None, None, `type` = Type.AvroSchema, schema = schema.asJson))

  val eventsTypesClient = EventTypes(nakadiUri)

  val events = List(
    AvroEvent(AvroRecordTest("foo_1", 1), Metadata(eventType = Some(eventTypeName))),
    AvroEvent(AvroRecordTest("foo_2", 2), Metadata(eventType = Some(eventTypeName)))
  )

  val expectedDataEvents = events.map(_.data).toSet

  val eventCounter                      = new AtomicInteger(0)
  val subscriptionClosed: AtomicBoolean = new AtomicBoolean(false)
  val streamComplete: Promise[Unit]     = Promise()

  override def beforeAll = Await.result(eventsTypesClient.create(eventType), 10 seconds)

  override def afterAll =
    Await.result(eventsTypesClient.delete(eventTypeName), 10 seconds)

  def publishAvroEvent = {
    val avroPublisher = AvroPublisher[AvroRecordTest](nakadiUri, None, eventTypeName, schema, eventsTypesClient)
    val future        = avroPublisher.publishAvro(events)
    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def consumeAvroEvent = {
    implicit val flowId: FlowId = Utils.randomFlowId()

    val allAuth      = List(AuthorizationAttribute("user", "*"))
    val subAuth      = SubscriptionAuthorization(allAuth, allAuth)
    val avroConsumer = AvroSubscriptions[AvroRecordTest](nakadiUri, None, eventTypeName, schema, eventsTypesClient)
    val subCreated = avroConsumer.create(
      Subscription(readFrom = Some("begin"),
                   id = None,
                   owningApplication = "App",
                   eventTypes = Some(List(eventTypeName)),
                   consumerGroup = Some("default"),
                   authorization = Some(subAuth)))

    val sub = Await.result(subCreated, 10 seconds)
    val eventsFuture = avroConsumer.eventsStreamedSourceAvro(
      subscriptionId = sub.id.get,
      streamConfig = Subscriptions.StreamConfig(batchLimit = Some(2), streamLimit = Some(100)))
    val result = Await.result(eventsFuture, 10 seconds)

    result.source.runForeach(subEvent =>
      subEvent.events.foreach(ls =>
        ls.foreach { ev =>
          if (expectedDataEvents.contains(ev.data))
            eventCounter.addAndGet(1)

          if (eventCounter.get() == 2) {
            streamComplete.complete(Success(()))

            avroConsumer.commitCursors(sub.id.get, SubscriptionCursor(List(subEvent.cursor)), result.streamId)

            avroConsumer.delete(sub.id.get)
          }
        }))

    streamComplete.future must be_==(()).await(0, timeout = 1 minutes)
  }

}
