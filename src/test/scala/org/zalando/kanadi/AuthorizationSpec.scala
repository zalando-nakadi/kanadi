package org.zalando.kanadi

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.specification.core.SpecStructure
import org.zalando.kanadi.Config
import org.zalando.kanadi.api._
import org.zalando.kanadi.models.{EventTypeName, SubscriptionId}

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

class AuthorizationSpec(implicit ec: ExecutionEnv) extends Specification with FutureMatchers with Config {
  override def is: SpecStructure = sequential ^ s2"""
    Create Event Type          $createEventType
    Get Event Type             $getEventType
    Create subscription        $createSubscription
    Get subscription           $getSubscription
    """

  val config = ConfigFactory.load()

  implicit val system = ActorSystem()
  implicit val http   = Http()

  val eventTypeName = EventTypeName(s"Kanadi-Test-Event-${UUID.randomUUID().toString}")

  eventTypeName.pp

  val OwningApplication = "KANADI"

  val consumerGroup = UUID.randomUUID().toString

  s"Consumer Group: $consumerGroup".pp

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

  def createEventType = (name: String) => {
    val future = eventsTypesClient.create(
      EventType(name = eventTypeName,
                owningApplication = OwningApplication,
                category = Category.Business,
                authorization = Some(authorization)))

    future must be_==(()).await(retries = 3, timeout = 10 seconds)
  }

  def getEventType = (name: String) => {
    val future = eventsTypesClient.get(eventTypeName).map(_.flatMap(_.authorization))
    future must beSome(authorization).await(retries = 3, timeout = 10 seconds)
  }

  def createSubscription = (name: String) => {
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
        subscription.id.pp
        currentSubscriptionId.complete(Success(subscription.id.get))
      case _ =>
    }

    future.map(x => (x.owningApplication, x.eventTypes)) must beEqualTo((OwningApplication, Some(List(eventTypeName))))
      .await(0, timeout = 5 seconds)
  }

  def getSubscription = (name: String) => {
    val future = for {
      subscriptionId <- currentSubscriptionId.future
      subscription   <- subscriptionsClient.get(subscriptionId)
    } yield subscription.flatMap(_.authorization)

    future must beSome(subscriptionAuthorization).await(retries = 3, timeout = 10 seconds)
  }

}
