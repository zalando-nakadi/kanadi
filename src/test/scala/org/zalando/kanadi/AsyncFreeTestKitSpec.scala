package org.zalando.kanadi

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKitBase
import org.scalatest.fixture
import org.scalatest.freespec.FixtureAsyncFreeSpec

class AsyncFreeTestKitSpec(_system: ActorSystem)
    extends FixtureAsyncFreeSpec
    with TestKitBase
    with fixture.AsyncTestDataFixture {
  implicit val system: ActorSystem = _system
}
