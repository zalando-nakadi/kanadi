# Resilience to Partial Outage and Partial Success

When publishing a batch of events with a [Events.publish](https://github.com/zalando-nakadi/kanadi/blob/d77479b5ef4a6837303fe71a9dc625e7bb8c573d/src/main/scala/org/zalando/kanadi/api/EventsInterface.scala#L9) API,

```scala
def publish[T](name: EventTypeName, events: List[Event[T]], fillMetadata: Boolean = true): Future[Unit]
```

the publishing could either _succeed_, _partially succeed_ or _fail_. On success the publishing operation returns `Future[Unit]` that resolves to a `Unit` value. On _partial success_ or _failure_ the `Future` fails with an exception:

- `EventValidation(batchItemResponse: List[BatchItemResponse])` - on partial success;
- `HttpServiceError | GeneralError | OtherError` - on failure;

On partial sucess, `EventValidation` exception contains `List[BatchItemResponse]` with [statuses](https://nakadi.io/manual.html#definition_BatchItemResponse) of submission for each event in the batch.

The Kanadi library contains strategies to handle publishig errors:

## Fail on Partial Success or Error (Default)

The publishing operation immediately fails if the server returns an error that batch was partially successful or failed.
In this case the application can retry the whole batch or retry only failed events, depending on type of exception.
The decision is up to the application. However, it should be noted, that the application **should not retry the whole batch without a backoff strategy**, otherwise it can create problems for server, when many clients retry the same batch over and over.

## Retry with Expponential Backoff

Kanadi has a configuration option `kanadi.http-config.failed-publish-event-retry` which allows Kanadi to automatically
resend events should they fail. The setting can also be set using the environment variable
`KANADI_HTTP_CONFIG_FAILED_PUBLISH_EVENT_RETRY`. By default this setting is `false` since enabling this can cause
events to be sent out of order, in other words you shouldn't enable it if you (or your consumers) rely on ordering
of events. Kanadi will only resend the events which actually failed to send and it will refuse to send
events which failed due to schema validation (since resending such events is pointless).

Since Nakadi will only fail to publish an event in extreme circumstances (i.e. under heavy load) the retry
uses an exponential backoff which can be configured with `kanadi.exponential-backoff-config` settings (see
`reference.conf` for information on the settings). If the maximum number of retries was reached, `Events.publish`
fails with the original `Events.Errors.EventValidation` exception.

## Note

No mater what strategy is used, the application should be prepared to handle the case when the server returns an error that batch was partially successful or failed.

If not properly handled,

- the application can get stuck in a loop of retrying the same batch over and over
- the application can increase load on the server, if it retries the whole batch without a backoff strategy
- the application can lose events, if the returned `Future` is not checked for errors (fire and forget).
