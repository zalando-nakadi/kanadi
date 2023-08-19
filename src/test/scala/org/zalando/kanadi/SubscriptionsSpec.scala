package org.zalando.kanadi

import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api.{Category, EventType, EventTypes, Events, Subscription, Subscriptions}
import org.zalando.kanadi.models.{EventTypeName, FlowId, SubscriptionId}

import scala.collection.parallel.mutable
import scala.concurrent.Future

class SubscriptionsSpec
    extends AsyncFreeTestKitSpec(ActorSystem("SubscriptionsSpec"))
    with PekkoTestKitBase
    with Matchers
    with Config {

  val config = ConfigFactory.load()

  val eventTypeName         = EventTypeName(s"Kanadi-Test-Event-${UUID.randomUUID().toString}")
  val OwningApplication     = "KANADI"
  val consumerGroup: String = UUID.randomUUID().toString
  val subscriptionsClient =
    Subscriptions(nakadiUri, None)
  val eventsClient = Events(nakadiUri, None)
  val eventsTypesClient =
    EventTypes(nakadiUri, None)
  val subscriptionIds: mutable.ParSet[SubscriptionId] = mutable.ParSet.empty

  pp(eventTypeName)
  pp(s"Consumer Group: $consumerGroup")

  "Create enough subscriptions to ensure that pagination is used" in { () =>
    implicit val flowId: FlowId = Utils.randomFlowId()
    pp(flowId)

    for {
      _ <- eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))
      createdSubscriptions <-
        Future.sequence(for {
          _ <- 1 to 22
          subscription =
            subscriptionsClient.create(
              Subscription(None, s"$OwningApplication-${UUID.randomUUID().toString}", Some(List(eventTypeName))))
        } yield { /*  */
          subscription.foreach { s =>
            subscriptionIds += s.id.get
          }
          subscription
        })
      retrievedSubscription <- Future.sequence(createdSubscriptions.map { subscription =>
                                 subscriptionsClient.createIfDoesntExist(subscription)
                               })
    } yield createdSubscriptions mustEqual retrievedSubscription
  }

}
