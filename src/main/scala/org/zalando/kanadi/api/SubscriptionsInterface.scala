package org.zalando.kanadi.api

import org.apache.pekko.stream.UniqueKillSwitch
import org.apache.pekko.stream.scaladsl.Source
import io.circe.Decoder
import org.zalando.kanadi.models.{EventTypeName, FlowId, StreamId, SubscriptionId}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait SubscriptionsInterface {
  def create(subscription: Subscription)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext): Future[Subscription]

  def createIfDoesntExist(subscription: Subscription)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Subscription]

  def list(owningApplication: Option[String] = None,
           eventType: Option[List[EventTypeName]] = None,
           limit: Option[Int] = None,
           offset: Option[Int] = None)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[SubscriptionQuery]

  def get(subscriptionId: SubscriptionId)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[Subscription]]

  def delete(subscriptionId: SubscriptionId)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Unit]

  def cursors(subscriptionId: SubscriptionId)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[SubscriptionCursor]]

  def commitCursors(subscriptionId: SubscriptionId,
                    subscriptionCursor: SubscriptionCursor,
                    streamId: StreamId,
                    eventBatch: Boolean)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[CommitCursorResponse]]

  def resetCursors(subscriptionId: SubscriptionId, subscriptionCursor: Option[SubscriptionCursorWithoutToken] = None)(
      implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Boolean]

  def eventsStrictUnsafe[T](subscriptionId: SubscriptionId,
                            maxUncommittedEvents: Option[Int] = None,
                            batchLimit: Option[Int] = None,
                            streamLimit: Option[Int] = None,
                            batchFlushTimeout: Option[FiniteDuration] = None,
                            streamTimeout: Option[FiniteDuration] = None,
                            streamKeepAliveLimit: Option[Int] = None)(implicit
      decoder: Decoder[List[Event[T]]],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[List[SubscriptionEvent[T]]]

  def eventsStreamedSource[T](subscriptionId: SubscriptionId,
                              connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                                Subscriptions.ConnectionClosedCallback { _ =>
                                  ()
                                },
                              streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig())(implicit
      decoder: Decoder[List[Event[T]]],
      flowId: FlowId,
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider): Future[Subscriptions.NakadiSource[T]]

  def eventsStreamed[T](subscriptionId: SubscriptionId,
                        eventCallback: Subscriptions.EventCallback[T],
                        connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                          Subscriptions.ConnectionClosedCallback { _ =>
                            ()
                          },
                        streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig(),
                        modifySourceFunction: Option[
                          Source[SubscriptionEvent[T], UniqueKillSwitch] => Source[SubscriptionEvent[T],
                                                                                   UniqueKillSwitch]] = None)(implicit
      decoder: Decoder[List[Event[T]]],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider
  ): Future[StreamId]

  def eventsStreamedManaged[T](subscriptionId: SubscriptionId,
                               eventCallback: Subscriptions.EventCallback[T],
                               connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                                 Subscriptions.ConnectionClosedCallback { _ =>
                                   ()
                                 },
                               streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig(),
                               modifySourceFunction: Option[
                                 Source[SubscriptionEvent[T], UniqueKillSwitch] => Source[SubscriptionEvent[T],
                                                                                          UniqueKillSwitch]] = None)(
      implicit
      decoder: Decoder[List[Event[T]]],
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider
  ): Future[StreamId]

  def eventsStreamedSourceManaged[T](subscriptionId: SubscriptionId,
                                     connectionClosedCallback: Subscriptions.ConnectionClosedCallback =
                                       Subscriptions.ConnectionClosedCallback { _ =>
                                         ()
                                       },
                                     streamConfig: Subscriptions.StreamConfig = Subscriptions.StreamConfig())(implicit
      decoder: Decoder[List[Event[T]]],
      flowId: FlowId,
      executionContext: ExecutionContext,
      eventStreamSupervisionDecider: Subscriptions.EventStreamSupervisionDecider): Future[Subscriptions.NakadiSource[T]]

  def closeHttpConnection(subscriptionId: SubscriptionId, streamId: StreamId): Boolean

  def stats(subscriptionId: SubscriptionId, showTimeLag: Boolean = false)(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext
  ): Future[Option[SubscriptionStats]]
}
