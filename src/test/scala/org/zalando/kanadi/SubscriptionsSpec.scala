package org.zalando.kanadi

import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.core.SpecStructure
import org.specs2.specification.{AfterAll, BeforeAll}
import org.zalando.kanadi.api.{Category, EventType, EventTypes, Events, Subscription, Subscriptions}
import org.zalando.kanadi.models.{EventTypeName, FlowId, SubscriptionId}

import scala.collection.parallel.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SubscriptionsSpec(implicit ec: ExecutionEnv) extends Specification with Config with BeforeAll with AfterAll {
  override def is: SpecStructure = sequential ^ s2"""
      Create enough subscriptions to ensure that pagination is used $createEnoughSubscriptionsToUsePagination
    """

  val config = ConfigFactory.load()

  implicit val system = ActorSystem()
  implicit val http   = Http()

  val eventTypeName         = EventTypeName(s"Kanadi-Test-Event-${UUID.randomUUID().toString}")
  val OwningApplication     = "KANADI"
  val consumerGroup: String = UUID.randomUUID().toString
  val subscriptionsClient =
    Subscriptions(nakadiUri, None)
  val eventsClient = Events(nakadiUri, None)
  val eventsTypesClient =
    EventTypes(nakadiUri, None)
  val subscriptionIds: mutable.ParSet[SubscriptionId] = mutable.ParSet.empty

  eventTypeName.pp
  s"Consumer Group: $consumerGroup".pp

  def createEventType = eventsTypesClient.create(EventType(eventTypeName, OwningApplication, Category.Business))

  override def beforeAll =
    Await.result(createEventType, 10 seconds)

  override def afterAll = {
    Await.result(
      for {
        res1 <- Future.sequence(subscriptionIds.toList.map(s => subscriptionsClient.delete(s)))
        res2 <- eventsTypesClient.delete(eventTypeName)
      } yield (res1, res2),
      10 seconds
    )
    ()
  }

  def createEnoughSubscriptionsToUsePagination = (name: String) => {
    implicit val flowId: FlowId = Utils.randomFlowId()
    flowId.pp(name)

    val createdSubscriptions = Future.sequence(for {
      _ <- 1 to 22
      subscription =
        subscriptionsClient.create(
          Subscription(None, s"$OwningApplication-${UUID.randomUUID().toString}", Some(List(eventTypeName))))
    } yield {
      subscription.foreach { s =>
        subscriptionIds += s.id.get
      }
      subscription
    })

    val retrievedSubscriptions = (for {
      subscriptions <- createdSubscriptions
      retrievedSubscription = Future.sequence(subscriptions.map { subscription =>
                                subscriptionsClient.createIfDoesntExist(subscription)
                              })
    } yield retrievedSubscription).flatMap(a => a)

    Await.result(createdSubscriptions, 10 seconds) mustEqual Await.result(retrievedSubscriptions, 10 seconds)
  }

}
