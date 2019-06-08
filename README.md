## Kanadi
[![codecov](https://codecov.io/gh/zalando-incubator/kanadi/branch/master/graph/badge.svg)](https://codecov.io/gh/zalando-incubator/kanadi)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.zalando/kanadi_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.zalando%22%20AND%20a%3A%22kanadi_2.12%22)
[![Gitter chat](https://badges.gitter.im/gitterHQ/gitter.png)](https://gitter.im/zalando-kanadi/Lobby)

Kanadi is [Nakadi](https://github.com/zalando/nakadi) client for Scala.

### Goals

* Uses [akka-http](http://doc.akka.io/docs/akka-http/current/scala.html) as the underlying HTTP client for streaming
* Uses [circe](https://github.com/circe/circe) for dealing with JSON
* Uses [akka-stream-json](https://github.com/mdedetrich/akka-streams-json) for streaming of JSON

The underlying goal is to have a high performance/throughput Nakadi client that uses streaming all the way down
(including json) that is easily configurable via akka-http.

### Non-Goals

* Persistence of events. Persistence is orthogonal concern to Kanadi, there are many different ways to do it 
(particularly when taking into account error handling, i.e. with acknowledgement of cursors the definition of "correct"
depends on the applications use case)
* Low level API. The Nakadi low level API is planned to be deprecated and its not going to be supported by Kanadi.
* Using another Json library "X". Kanadi relies on Circe as well Circe's streaming infrastructure, abstracting over this
isn't really technically feasible without sacrificing performance.
* Using another IO/Task/Future library "X". Minimal friction with the ecosystem is a design goal of Kanadi which
means we need to consider the lifecycle and dependencies of certain task types (to date none of the other IO/Task/Future
types have had a good history when it comes to binary compatibility or lifecycle longevity). Although `Future` has its
drawbacks, its ultra stable and portable across the Scala ecosystem.
* Using another streaming library "X". Although there are other very good implementations of streaming libraries (such
as [Monix](https://monix.io/)), [akka-stream](https://doc.akka.io/docs/akka/2.5/stream/index.html) is still the best when
it comes to binary compatibility. It also comes with a lot of support for monitoring. Note that interopting between
streams is possible with any other stream that follows the [Reactive Streams](http://www.reactive-streams.org/) protocol.
* Scala 2.10.x support since akka-streams/akka-http does not support Scala 2.10

### Current status

Kanadi currently implements the high level subscription API which allows clients to use Nakadi in a stateless fashion
(i.e. clients don't have to worry about storing cursors).

Its currently considered stable as its been used in production to handle non trivial amounts of data for over
a year, including handling events from Zalando's main website search.

### Installation

Kanadi is currently deployed to OSS Sonatype. For Circe 0.10.x use Kanadi 0.3.x

```sbt
libraryDependencies ++= Seq(
    "org.zalando" %% "kanadi" % "0.3.4"
)
```

Otherwise if you need a Kanadi built against Circe 0.9.x use Kanadi 0.2.x

```sbt
libraryDependencies ++= Seq(
    "org.zalando" %% "kanadi" % "0.2.7"
)
```

Kanadi is currently built against Scala 2.11.x and 2.12.x

### Usage

#### Consuming Events (High level API)

Firstly in order to consume an event, we need to figure out what type of event we are dealing with. Nakadi supports
3 types of events, business/data and undefined. The reason why its important to figure out what these events are is
because the Circe `Encoder`'s and `Decoder`'s for these events will differ depending on what the event type is. The
easiest way to determine what type of event you are dealing with is to look at the Nakadi JSON for the event.

##### Data Change Event
The event is a data change event if it contains `data_op` field in the root JSON object, i.e.

```json
{
    "metadata": {
        "occurred_at": "1984-03-03T12:30:12.000Z",
        "eid": "fee9be69-4a4c-47e2-a193-fd1003f6742b",
        "partition": "0",
        "event_type": "address-change-event",
        "received_at": "2017-10-10T09:07:49.113Z",
        "flow_id": "QHTGz3vrJhkHicPutHo88u80",
        "version": "0.1.0"
    },
    "data_op": "C",
    "data": {
        "occurred_at": "1984-03-03T12:30:12.000Z",
        "country": "DE",
        "event_type": "ADDED",
        "customer_id": "3248947",
        "locale": "de-DE",
        "client_id": "6d359e0f-8b7a-40c0-aeb5-c46d164a8074",
        "address": {
            "street_number": 7,
            "street_name": "Wurststrasse",
            "post_code": 106398
        }
    },
    "data_type": "address-change.events"
}
```

Since we have `"data_op": "C"`, this is going to be a datachange event

In this case since the data for the body will **always** be contained within the JSON `data` field, Kanadi will
automatically handle reading the JSON data from within the `data` object, so your `Encoder`/`Decoder` doesn't need
to take into account any nesting. You also do not need to take into account parsing the `metadata`, `data_op` or 
`data_type`, this will be handled for you.
 
A sample `Encoder`/`Decoder` for the above event might look like this.

```scala
import io.circe._
import io.circe.syntax._

final case class Address(streetNumber: Int, streetName: String, postCode: Int)

implicit val addressEncoder: Encoder[Address] = Encoder.forProduct3(
  "street_number",
  "street_name",
  "post_code"
)(x => Address.unapply(x).get)

final case class AddressChangeEvent(
  country: String,
  eventType: String,
  customerId: String,
  locale: String,
  clientId: String,
  address: Address
)

implicit val addressChangeEventEncoder: Encoder[AddressChangeEvent] = Encoder.forProduct6(
  "country",
  "event_type",
  "customer_id",
  "locale",
  "client_id",
  "address"
)(x => AddressChangeEvent.unapply(x).get)

implicit val addressChangeEventDecoder: Decoder[AddressChangeEvent] = Decoder.forProduct6(
  "country",
  "event_type",
  "customer_id",
  "locale",
  "client_id",
  "address"
)(AddressChangeEvent.apply)
```

##### Business Event
Like the data change event, the business event also contains `metadata` within the JSON event data, it will however 
not contain  the `data_op` JSON field. The actual data of the event can be located anywhere (there is no validation
or guideline where you should place the actual event data).

This means that both

```json
{
    "date": "Oct 5, 2017 9:54:08 PM",
    "metadata": {
        "occurred_at": "2017-10-05T21:54:08Z",
        "eid": "2e828815-ff5c-4a04-8ec1-35b24630ed3e",
        "event_type": "song-query",
        "partition": "0",
        "received_at": "2017-10-05T21:54:08.974Z",
        "flow_id": "tlKVTSIVfgBpZceuOfe5FS1e",
        "version": "1.0.0"
    },
    "body": {
        "song": "You Spin Me Round (Like a Record)",
        "album": "Youthquake",
        "artist": "Dead Or Alive",
        "year": 1985,
        "duration": "3:20"
    }
}
```

and

```json
{
    "date": "Oct 5, 2017 9:54:08 PM",
    "metadata": {
        "occurred_at": "2017-10-05T21:54:08Z",
        "eid": "2e828815-ff5c-4a04-8ec1-35b24630ed3e",
        "event_type": "song-query",
        "partition": "0",
        "received_at": "2017-10-05T21:54:08.974Z",
        "flow_id": "tlKVTSIVfgBpZceuOfe5FS1e",
        "version": "1.0.0"
    },
    "song": "You Spin Me Round (Like a Record)",
    "album": "Youthquake",
    "artist": "Dead Or Alive",
    "year": 1985,
    "duration": "3:20"
}
```

Are valid business events, so you need to take this into account using your `Encoder`/`Decoder`. Assuming you want to
encode the first version (where the data is contained within the JSON `body` field) you would do something like this

```scala
import io.circe._
import io.circe.syntax._

final case class SongQueryEvent(
  song: String,
  album: String,
  artist: String,
  year: Int,
  duration: String
)

implicit val songQueryEventEncoder: Encoder[SongQueryEvent] = Encoder.instance[SongQueryEvent] { songQueryEvent =>

  val bodyEncoder: Encoder[SongQueryEvent] = Encoder.forProduct5(
    "song",
    "album",
    "artist",
    "year",
    "duration"
  )(x => SongQueryEvent.unapply(x).get)

  Json.obj(
    "body" -> bodyEncoder(songQueryEvent)
  )
}

implicit val songQueryEventDecoder: Decoder[SongQueryEvent] = Decoder.instance[SongQueryEvent](
  _.downField("body").as[SongQueryEvent](
    Decoder.forProduct5(
      "song",
      "album",
      "artist",
      "year",
      "duration"
    )(SongQueryEvent.apply)
  )
)

```

This way you can keep your `case class` which contains the data free of any nesting that may occur, we take care of the
`body` nesting in the `Encoder`/`Decoder` which keeps our `case class` `SongQueryEvent` clean.

##### Undefined Event

An undefined event is any event which doesn't fulfill the above event/s which means it has no restrictions/validation
on the JSON object whatsoever. This means that any kind of JSON has to be explicitly handled in your `Encoder/Decoder`

Now that we know how to write `Encoder`'s and `Decoder`'s for our event, lets write one for our sample event (in this case
lets assume its a business event with the event data contained in the root of the JSON object)

##### Getting started

So first lets write a case class representing our data

```scala
final case class SomeEvent(firstName: String, lastName: String)
```

The equivalent JSON of this for some business event would be

```json
{
  "metadata": {
    "occurred_at": "2017-06-18T20:18:29Z",
    "eid": "e37c2ad1-b7f2-4fd9-85cb-6525cb9e93bf",
    "event_type": "some-event-publisher",
    "partition": "0",
    "received_at": "2017-06-18T20:18:29.111Z",
    "flow_id": "516c01de-f924-4492-b83a-e0a04b108312",
    "version": "1.0.0"
  },
  "first_name": "Bob",
  "last_name": "James"
}
```

Note how we are only defining the case class to contain the actual data of the event (`first_name` and `last_name`).

We then need to define a Circe Encoder/Decoder (for more info, read the Circe
documentation [here](https://circe.github.io/circe/))

```scala
import io.circe._
import io.circe.syntax._

final case class SomeEvent(firstName: String, lastName: String)

object SomeEvent {
  implicit val someEventEncoder: Encoder[SomeEvent] = Encoder.forProduct2(
    "first_name",
    "last_name"
  )(x => SomeEvent.unapply(x).get)
  
  implicit val someEventDecoder: Decoder[SomeEvent] = Decoder.forProduct2(
    "first_name",
    "last_name"
  )(SomeEvent.apply)
}
```

Note that we have defined the implicits in the `SomeEvent`'s companion object so they
automatically get picked up without any imports

Then we will define a subscription, we need to do this in order to consume an event's in the high level API.
We will also import the required imports that we need for akka-http (these need to be available as implicits)

```scala
import io.circe._
import io.circe.syntax._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.zalando.kanadi.api.Subscription
import org.zalando.kanadi.api.Subscriptions
import org.zalando.kanadi.models.EventTypeName
import org.zalando.kanadi.Config
import scala.concurrent.ExecutionContext.Implicits.global

final case class SomeEvent(firstName: String, lastName: String)

object SomeEvent {
  implicit val someEventEncoder: Encoder[SomeEvent] = Encoder.forProduct2(
    "first_name",
    "last_name"
  )(x => SomeEvent.unapply(x).get)
  
  implicit val someEventDecoder: Decoder[SomeEvent] = Decoder.forProduct2(
    "first_name",
    "last_name"
  )(SomeEvent.apply)
}

object Main extends App with Config {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem()
  implicit val http = Http()
  implicit val materializer = ActorMaterializer()
  
  val subscriptionsClient = Subscriptions(nakadiUri)
  
  def createOrGetSubscription = subscriptionsClient.createIfDoesntExist(
    Subscription(
      None,
      "Kanadi-Some-Event-Example",
      Option(List(EventTypeName("some-event-publisher")))
    ))
}
```

The `Subscriptions.createIfDoesntExist` is a helper method which will create a subscription if it doesn't exist,
however if it does then it will just return the subscription. Note that in Nakadi, `Subscription`'s are uniquely
defined by a combination of `owningApplication`, `eventTypes` and `consumerGroup` .

Next we will define a callback, this will get executed whenever we consume an event

```scala
import io.circe._
import io.circe.syntax._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.zalando.kanadi.api.Subscription
import org.zalando.kanadi.api.Subscriptions
import org.zalando.kanadi.api.Subscriptions._
import org.zalando.kanadi.api.Event
import org.zalando.kanadi.models.EventTypeName
import org.zalando.kanadi.Config
import scala.concurrent.ExecutionContext.Implicits.global

final case class SomeEvent(firstName: String, lastName: String)

object SomeEvent {
  implicit val someEventEncoder: Encoder[SomeEvent] = Encoder.forProduct2(
    "first_name",
    "last_name"
  )(x => SomeEvent.unapply(x).get)
  
  implicit val someEventDecoder: Decoder[SomeEvent] = Decoder.forProduct2(
    "first_name",
    "last_name"
  )(SomeEvent.apply)
}

object Main extends App with Config {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem()
  implicit val http = Http()
  implicit val materializer = ActorMaterializer()
  
  val subscriptionsClient = Subscriptions(nakadiUri)

  def createOrGetSubscription = subscriptionsClient.createIfDoesntExist(
    Subscription(
      None,
      "Kanadi-Some-Event-Example",
      Option(List(EventTypeName("some-event-publisher")))
    ))
    
  val callback = EventCallback.successAlways[SomeEvent] { eventCallbackData =>
    eventCallbackData.subscriptionEvent.events.getOrElse(List.empty).foreach{ 
      case e: Event.Business[SomeEvent] =>
        println(s"Received Event ${e.metadata.eid}")
        println(s"Flow Id for callback is ${eventCallbackData.flowId}")
        println(s"First name is ${e.data.firstName}, last name is ${e.data.lastName}")
      case e: Event.DataChange[_] =>
        println("Wrong type of event")
      case e: Event.Undefined[_] =>
        println("Wrong type of event")
    }
  }
}
```

The first thing to note is that we are using `EventCallback.successAlways`. In most cases
you want to use this callback, if you actually receive the event you should commit a success that
you have received the event. Even if your application doesn't successfully handle the business logic
in response to your event, this should be handled by persisting the event and handling it another way.

One thing to note is that any potential errors caused by callbacks are caught using `scala.util.control.NonFatal`.
If the callback throws an `Exception` then it will be logged. This is intentionally done to prevent callback exceptions
from terminating the stream. Note that it is recommended that the callback itself handles errors.

Now we will do the simply version of subscribing to the event, this will handle reconnects in case of
server disconnects or no empty slots/cursor reset and is the typical use case that most clients will want to use. The
delays can be configured using `KanadiHttpConfig.noEmptySlotsCursorResetRetryDelay` and 
`KanadiHttpConfig.serverDisconnectRetryDelay`, see `reference.conf` for more info.

```scala
import io.circe._
import io.circe.syntax._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.zalando.kanadi.api.Subscription
import org.zalando.kanadi.api.Subscriptions
import org.zalando.kanadi.api.Subscriptions._
import org.zalando.kanadi.api.Event
import org.zalando.kanadi.models.EventTypeName
import org.zalando.kanadi.models.SubscriptionId
import org.zalando.kanadi.Config
import scala.concurrent.ExecutionContext.Implicits.global

final case class SomeEvent(firstName: String, lastName: String)

object SomeEvent {
  implicit val someEventEncoder: Encoder[SomeEvent] = Encoder.forProduct2(
    "first_name",
    "last_name"
  )(x => SomeEvent.unapply(x).get)
  
  implicit val someEventDecoder: Decoder[SomeEvent] = Decoder.forProduct2(
    "first_name",
    "last_name"
  )(SomeEvent.apply)
}

object Main extends App with Config {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem()
  implicit val http = Http()
  implicit val materializer = ActorMaterializer()
  
  val subscriptionsClient = Subscriptions(nakadiUri)

  def createOrGetSubscription = subscriptionsClient.createIfDoesntExist(
    Subscription(
      None,
      "Kanadi-Some-Event-Example",
      Option(List(EventTypeName("some-event-publisher")))
    ))
    
  val callback = EventCallback.successAlways[SomeEvent] { eventCallbackData =>
    eventCallbackData.subscriptionEvent.events.getOrElse(List.empty).foreach{ 
      case e: Event.Business[SomeEvent] =>
        println(s"Received Event ${e.metadata.eid}")
        println(s"Flow Id for callback is ${eventCallbackData.flowId}")
        println(s"First name is ${e.data.firstName}, last name is ${e.data.lastName}")
      case e: Event.DataChange[_] =>
        println("Wrong type of event")
      case e: Event.Undefined[_] =>
        println("Wrong type of event")
    }
  }
  
  def createStream(subscriptionId: SubscriptionId) = subscriptionsClient.eventsStreamedManaged[SomeEvent](
    subscriptionId,
    callback
  )

}
```

In this case, if we try to subscribe and it fails (due to not getting enough slots), we will
reconnect after a minute. Note that when you are deploying a new instance of your project and you have
more instances than partitions the above code will handle this situation (when the old instance will terminate
and disconnect from the stream it will free up some slots, so the new instance will eventually reconnect)

#### Automatically retrying sending of events

Kanadi has a configuration option `kanadi.http-config.failed-publish-event-retry` which allows Kanadi to automatically
resend events should they fail. The setting can also be set using the environment variable
`KANADI_HTTP_CONFIG_FAILED_PUBLISH_EVENT_RETRY`. By default this setting is `false` since enabling this can cause
events to be sent out of order, in other words you shouldn't enable it if you (or your consumers) rely on ordering
of events. Kanadi will only resend the events which actually failed to send and it will refuse to send
events which failed due to schema validation (since resending such events is pointless).

Since Nakadi will only fail to publish an event in extreme circumstances (i.e. under heavy load) the retry
uses an exponential backoff which can be configured with `kanadi.exponential-backoff-config` settings (see 
`reference.conf` for information on the settings). If reach the maximum number of retries then `Events.publish`
will fail with the original `Events.Errors.EventValidation` exception.

#### Modifying the akka-stream source

It is possible to modify the underlying akka stream when using `Subscriptions.eventsStreamed` or
`Subscriptions.eventsStreamedManaged` by setting the `modifySourceFunction` parameter. One common use case for doing 
this is to provide some manual throttling, i.e. you can do something like this

```scala

import io.circe._
import io.circe.syntax._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ThrottleMode}
import com.typesafe.config.ConfigFactory
import org.zalando.kanadi.api.Subscription
import org.zalando.kanadi.api.Subscriptions
import org.zalando.kanadi.api.Subscriptions._
import org.zalando.kanadi.api.Event
import org.zalando.kanadi.models.EventTypeName
import org.zalando.kanadi.models.SubscriptionId
import org.zalando.kanadi.Config
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

final case class SomeEvent(firstName: String, lastName: String)

object SomeEvent {
  implicit val someEventEncoder: Encoder[SomeEvent] = Encoder.forProduct2(
    "first_name",
    "last_name"
  )(x => SomeEvent.unapply(x).get)
  
  implicit val someEventDecoder: Decoder[SomeEvent] = Decoder.forProduct2(
    "first_name",
    "last_name"
  )(SomeEvent.apply)
}

object Main extends App with Config {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem()
  implicit val http = Http()
  implicit val materializer = ActorMaterializer()
  
  val subscriptionsClient = Subscriptions(nakadiUri)

  def createOrGetSubscription = subscriptionsClient.createIfDoesntExist(
    Subscription(
      None,
      "Kanadi-Some-Event-Example",
      Option(List(EventTypeName("some-event-publisher")))
    ))
    
  val callback = EventCallback.successAlways[SomeEvent] { eventCallbackData =>
    eventCallbackData.subscriptionEvent.events.getOrElse(List.empty).foreach{ 
      case e: Event.Business[SomeEvent] =>
        println(s"Received Event ${e.metadata.eid}")
        println(s"Flow Id for callback is ${eventCallbackData.flowId}")
        println(s"First name is ${e.data.firstName}, last name is ${e.data.lastName}")
      case e: Event.DataChange[_] =>
        println("Wrong type of event")
      case e: Event.Undefined[_] =>
        println("Wrong type of event")
    }  
  }
  
  def createStream(subscriptionId: SubscriptionId) = subscriptionsClient.eventsStreamedManaged[SomeEvent](
    subscriptionId,
    callback,
    modifySourceFunction = Some(_.throttle(100, 1 second, 300, ThrottleMode.shaping))
  )
}
```

The above will provide throttling for each batch of events. Note that a better way to handle this case of throttling
directly may be to use the source stream directly (detailed [below](#using-the-source-stream-directly)). This is because
akka-streams are fully 
[backpressured](https://doc.akka.io/docs/akka/2.5.3/scala/stream/stream-flows-and-basics.html#back-pressure-explained).

#### Using the source stream directly

There is also a `Subscriptions.eventsStreamedSource` method which exposes the Nakadi stream as an akka-stream `Source`,
this allows you to directly combine the Nakadi `Source` as part of another graph.

NOTE: When you use this method directly, you also need to register the killswitch using the
`Subscriptions.addStreamToKillSwitch` method (at least if you plan on using the `Subscriptions.closeHttpConnection`
method to kill a stream). Also `Subscriptions.eventsStreamedSourceManaged` method exists which will handle the
reconnect for the `Subscriptions.Errors.NoEmptySlotsOrCursorReset(_)` however it will **NOT** handle server disconnects,
this needs to be done manually by calling `Subscriptions.eventsStreamedSourceManaged` since the stream is not
materialized yet (all you have is a reference to an uninitialized stream), read
[here](https://doc.akka.io/docs/akka/2.5.4/scala/general/stream/stream-design.html#resulting-implementation-constraints)
for more info.

You also have to be very careful of how you handle errors particularly in the context of committing cursors. Since you
are working with the source directly you need to make sure that you commit cursors even in the case of stream errors.
Cursors will still be committed when [failing to parse json](#failure-on-parsing-event-json) but its still your
responsibility to commit the cursor for both happy case and error case handling.

#### Failure on parsing event JSON

When Kanadi parses events data, it parses the subscription event JSON object in two parts (in parallel).
It will parse the event information (which contains data such as the cursor and event metadata) separately
from the actual event data itself.

This is done deliberately since if you have an incorrect `io.circe.Decoder` for some event data, it
will by default terminate the stream (this is also a somewhat common error). This means on success of
parsing the entire JSON payload, Kanadi will combine the data to form a
`org.zalando.kanadi.api.Subscriptions.SubscriptionEvent`, however if it fails decoding the JSON then you will get a 
`org.zalando.kanadi.api.Subscriptions.EventJsonParsingException` which will contain the JSON parsing exception as well
as the basic cursor/event information (so you are still able to commit the cursor token).

So that the stream doesn't get in an endless loop when dealing with decoding errors, we use a
`akka.stream.Supervision.Decider`. The provided default `Supervision.Decider` will log details about the JSON parsing
error as well as committing the cursor. It is provided as an `implicit val` that is located 
in `org.zalando.kanadi.api.Subscriptions.defaultEventStreamSupervisionDecider`.

You can also provide an alternate supervision decider (make sure that you don't import the default one).

```scala
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.mdedetrich.webmodels.FlowId
import org.zalando.kanadi.models.{SubscriptionId, StreamId}
import org.zalando.kanadi.api.{Subscriptions, SubscriptionCursor}
import org.zalando.kanadi.api.Subscriptions.EventStreamContext
import akka.stream.Supervision
import org.zalando.kanadi.Config

object Main extends App with Config {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem()
  implicit val http = Http()
  implicit val materializer = ActorMaterializer()
  
  val subscriptionsClient = Subscriptions(nakadiUri)

  import org.mdedetrich.webmodels.FlowId
  import org.zalando.kanadi.models.{SubscriptionId, StreamId}
  import org.zalando.kanadi.api.{Subscriptions, SubscriptionCursor}
  import akka.stream.Supervision
  
  
  implicit val mySupervisionDecider: Subscriptions.EventStreamSupervisionDecider =
    Subscriptions.EventStreamSupervisionDecider {
      eventStreamContext: EventStreamContext =>
        {
          case parsingException: Subscriptions.EventJsonParsingException =>
            // Lets commit the cursor, else we will cause Nakadi to disconnect
            eventStreamContext.subscriptionsClient.commitCursors(eventStreamContext.subscriptionId,
                                                                SubscriptionCursor(
                                                                List(parsingException.subscriptionEventInfo.cursor)),
                                                                eventStreamContext.streamId)
            // Do some addition operations, i.e. store the event in some persistent storage 
      
      
            // Specify to resume the stream, else Kanadi will disconnect the stream which will cause a
            // reconnect, causing an endless loop                
            Supervision.Resume
          case _ => Supervision.Stop
        }
    }
}
```

Then to use this `mySupervisionDecider` you can simply provide it as a parameter to
`subscriptionsClient.eventsStreamedManaged`, i.e.

```scala
import io.circe._
import io.circe.syntax._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.mdedetrich.webmodels.FlowId
import org.zalando.kanadi.models.{SubscriptionId, StreamId}
import org.zalando.kanadi.api.{Subscriptions, SubscriptionCursor}
import org.zalando.kanadi.api.Subscriptions.EventStreamContext
import akka.stream.Supervision
import org.zalando.kanadi.Config
import org.zalando.kanadi.api.Event
import org.zalando.kanadi.api.Subscription
import org.zalando.kanadi.api.Subscriptions
import org.zalando.kanadi.api.Subscriptions.EventCallback

final case class SomeEvent(firstName: String, lastName: String)

object SomeEvent {
  implicit val someEventEncoder: Encoder[SomeEvent] = Encoder.forProduct2(
    "first_name",
    "last_name"
  )(x => SomeEvent.unapply(x).get)
  
  implicit val someEventDecoder: Decoder[SomeEvent] = Decoder.forProduct2(
    "first_name",
    "last_name"
  )(SomeEvent.apply)
}

object Main extends App with Config {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem()
  implicit val http = Http()
  implicit val materializer = ActorMaterializer()
  
  val subscriptionsClient = Subscriptions(nakadiUri)

  import org.mdedetrich.webmodels.FlowId
  import org.zalando.kanadi.models.{SubscriptionId, StreamId}
  import org.zalando.kanadi.api.{Subscriptions, SubscriptionCursor}
  import akka.stream.Supervision
  
  implicit val mySupervisionDecider: Subscriptions.EventStreamSupervisionDecider =
    Subscriptions.EventStreamSupervisionDecider {
      eventStreamContext: EventStreamContext =>
        {
          case parsingException: Subscriptions.EventJsonParsingException =>
            // Lets commit the cursor, else we will cause Nakadi to disconnect
            eventStreamContext.subscriptionsClient.commitCursors(eventStreamContext.subscriptionId,
                                                                SubscriptionCursor(
                                                                List(parsingException.subscriptionEventInfo.cursor)),
                                                                eventStreamContext.streamId)
            // Do some addition operations, i.e. store the event in some persistent storage 
      
      
            // Specify to resume the stream, else Kanadi will disconnect the stream which will cause a
            // reconnect, causing an endless loop                
            Supervision.Resume               
          case _ => Supervision.Stop
        }
    }
  
  val callback = EventCallback.successAlways[SomeEvent] { eventCallbackData =>
    eventCallbackData.subscriptionEvent.events.getOrElse(List.empty).foreach{ 
      case e: Event.Business[SomeEvent] =>
        println(s"Received Event ${e.metadata.eid}")
        println(s"Flow Id for callback is ${eventCallbackData.flowId}")
        println(s"First name is ${e.data.firstName}, last name is ${e.data.lastName}")
      case e: Event.DataChange[_] =>
        println("Wrong type of event")
      case e: Event.Undefined[_] =>
        println("Wrong type of event")
    }
  }
  
  def createStream(subscriptionId: SubscriptionId) = subscriptionsClient.eventsStreamedManaged[SomeEvent](
    subscriptionId,
    callback
  )
  
}
```

Another way of handling this case is to simply decode the event as a `JsonObject` rather than using a custom case
class with a decoder (i.e. `subscriptionsClient.eventsStreamedManaged[JsonObject]`). This wont fail (since any
event data from Nakadi is going to be a valid JSON object).

#### FlowId/Oauth2Token

If you want to specify a flowId, you need to have an implicit flowId in scope, i.e.

```scala
import org.mdedetrich.webmodels.FlowId
import java.util.UUID

implicit val flowId = FlowId(UUID.randomUUID.toString)
```

If you don't specify a `FlowId`, Kanadi will automatically generate one from you and put it in the header for requests
(although the only way to get back this automatically generated `FlowId` is to turn on `DEBUG` level debugging for
requests).

For OAuth2 implicit flow authentication, you need to provide a function that defines how you retrieve the token, i.e.

```scala
import com.typesafe.config.ConfigFactory
import org.mdedetrich.webmodels.{OAuth2Token, OAuth2TokenProvider}
import org.zalando.kanadi.api.Subscriptions
import org.zalando.kanadi.models._
import org.zalando.kanadi.Config
import scala.concurrent.Future  

object Main extends App with Config {
  val config = ConfigFactory.load()
  
  val oAuth2TokenProvider = Option(
    OAuth2TokenProvider(
      () => Future.successful(OAuth2Token(sys.props("TOKEN"))))
  )
    
  val subscriptionsClient = Subscriptions(nakadiUri, oAuth2TokenProvider)
}
```

Note that Kanadi will automatically censor the tokenId (this is done by overriding the `.toString` method for the
`RawHeader`). If you want to turn off this functionality then you need to set `kanadi.http-config.censor-oAuth2-token`
to `false`.

#### Logging

Since Kanadi uses logback, to access `DEBUG` level logging (helpful in case of problems) you can simply add the
following to your `logback.xml`

```xml
<logger name="org.zalando.kanadi.api.Subscriptions" level="DEBUG"/>
```

This will add `DEBUG` level logging for all calls for Subscription.

Kanadi will also add the current FlowId to the [MDC](https://logback.qos.ch/manual/mdc.html) to all log level requests.
To log this MDC variable you simply need to add the `flow_id` MDC variable to your logback pattern, i.e. if you have

```xml
<encoder>
    <pattern>[%level] %logger{0} - %msg%n</pattern>
</encoder>
```

You can simply add

```xml
<encoder>
    <pattern>[%level] flow_id=%X{flow_id} %logger{0} - %msg%n</pattern>
</encoder>
```

And this will print the Flow Id as `flow_id` if it exists in the request.

### Technical Details

When listening to events via Nakadi (with `org.zalando.kanadi.api.Subscriptions.eventsStreamed` or related functions)
Kanadi will make a single dedicated Http connection rather than using the standard connection pool. This is so we don't
starve the connection pool with long living requests.

For this reason, it may be necessary to increase the `akka.http.host-connection-pool.max-open-requests` setting,
although it should be fine if you only have a single stream open for listening to events.

### Testing

Tests are located [here](https://github.bus.zalan.do/grip/kanadi/tree/master/src/test/scala). The tests are complete
integration tests, and we generate random event types to isolate our tests and make sure they are deterministic.

### Monitoring

Since this project uses akka-http, you can use either 
[Cinnamon](https://developer.lightbend.com/docs/cinnamon/2.5.x/introduction/introduction.html)
(for lightbend enterprise subscriptions) or [Kamon](https://github.com/kamon-io/kamon-akka-http) to monitor the Nakadi
streams on a deeper level.

### TODO

* Provide a benchmark/stress testing suite to battle test Kanadi against really high Nakadi payloads.
* No new features are currently planned as we have coverage of the Nakadi API and we deliberately try to limit the
scope of the project to just processing Nakadi events.
* Migrate README.md to github pages as a better documentation format.
* Migrate to ScalaTest for testing as it has better `Future` support.
* Investigate using akka-http's `akka.http.scaladsl.util.FastFuture` rather than normal `Future` in `for` comprehensions
to improve performance a bit.

### Known Bugs

There currently aren't any known bugs directly related to Kanadi, it has been used in production for a year and a half
and most bugs (as far as we are aware) have been ironed out.

### Contact

The `MAINTAINERS` file provides contact details.

### LICENSE

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
