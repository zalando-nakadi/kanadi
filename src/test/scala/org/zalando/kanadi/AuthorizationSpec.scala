package org.zalando.kanadi

import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.api._
import org.zalando.kanadi.models.{EventTypeName, SubscriptionId}

import scala.concurrent.Promise
import scala.util.Success

class AuthorizationSpec
    extends AsyncFreeTestKitSpec(ActorSystem("AuthorizationSpec"))
    with PekkoTestKitBase
    with Matchers
    with Config {

  val config = ConfigFactory.load()

  val eventTypeName = EventTypeName(s"Kanadi-Test-Event-${UUID.randomUUID().toString}")

  pp(eventTypeName)

  val OwningApplication = "KANADI"

  val consumerGroup = UUID.randomUUID().toString

  pp(s"Consumer Group: $consumerGroup")

  val subscriptionsClient =
    Subscriptions(nakadiUri, None)
  val eventsClient = Events(nakadiUri, None)
  val eventsTypesClient =
    EventTypes(nakadiUri, None)

  val currentSubscriptionId: Promise[SubscriptionId] = Promise()

  val authorization = EventTypeAuthorization(
    List(AuthorizationAttribute("user", "adminClientId")),
    List(AuthorizationAttribute("user", "adminClientId")),
    List(AuthorizationAttribute("user", "adminClientId"))
  )

  val subscriptionAuthorization = SubscriptionAuthorization(
    List(AuthorizationAttribute("user", "adminClientId")),
    List(AuthorizationAttribute("user", "adminClientId"))
  )

  "Create Event Type" in { () =>
    val future = eventsTypesClient.create(
      EventType(name = eventTypeName,
                owningApplication = OwningApplication,
                category = Category.Business,
                authorization = Some(authorization)))

    future.map(_ => succeed)
  }

  "Get Event Type" in { () =>
    val future = eventsTypesClient.get(eventTypeName).map(_.flatMap(_.authorization))
    future.map(result => result must contain(authorization))
  }

  "Create subscription" in { () =>
    val future = subscriptionsClient.createIfDoesntExist(
      Subscription(
        id = None,
        owningApplication = OwningApplication,
        eventTypes = Some(List(eventTypeName)),
        consumerGroup = Some(consumerGroup),
        authorization = Some(subscriptionAuthorization)
      ))

    future.onComplete {
      case scala.util.Success(subscription) =>
        pp(subscription.id)
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map { result =>
      (result.owningApplication, result.eventTypes) mustEqual ((OwningApplication, Some(List(eventTypeName))))
    }
  }

  "Get subscription" in { () =>
    val future = for {
      subscriptionId <- currentSubscriptionId.future
      subscription   <- subscriptionsClient.get(subscriptionId)
    } yield subscription.flatMap(_.authorization)

    future.map(result => result must contain(subscriptionAuthorization))
  }

}
