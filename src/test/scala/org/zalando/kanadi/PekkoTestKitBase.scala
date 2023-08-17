package org.zalando.kanadi

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.{Http, HttpExt}
import org.apache.pekko.testkit.{TestKit, TestKitBase}
import org.scalactic.Prettifier
import org.scalatest.{BeforeAndAfterAll, FixtureAsyncTestSuite, TestData, fixture}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

trait PekkoTestKitBase extends TestKitBase with BeforeAndAfterAll with fixture.AsyncTestDataFixture {
  this: FixtureAsyncTestSuite =>
  implicit val system: ActorSystem

  implicit val http: HttpExt = Http()

  override protected def afterAll(): Unit = {
    val future = http.shutdownAllConnectionPools().map(_ => TestKit.shutdownActorSystem(system))(system.dispatcher)
    Await.result(future, 10 seconds)
  }

  def pp(obj: Any)(implicit testData: TestData = null) = {
    val string =
      if (testData != null)
        s"${testData.name}: ${Prettifier.default(obj)}"
      else
        Prettifier.default(obj)

    println(string)
  }
}
