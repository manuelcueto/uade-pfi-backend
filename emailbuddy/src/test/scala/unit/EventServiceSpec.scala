package unit

import java.io.File

import cats.effect.IO
import org.cueto.pfi.domain.Event
import org.cueto.pfi.domain.Event.UserEvent
import org.cueto.pfi.service.EventServiceAlg
import org.cueto.pfi.stream.EventTrackerAlg
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EventServiceSpec extends AnyWordSpec with Matchers {

  "mailOpened" should {
    "fail if failing to track event" in new TestContext {
      val failingTracker = eventTracker(IO.raiseError(new Exception))

      an[Exception] should be thrownBy EventServiceAlg.impl(failingTracker).mailOpened(1, 1).unsafeRunSync()
    }

    "return unit when track is successful" in new TestContext {
      val successfulTracker = eventTracker(IO(()))

      EventServiceAlg.impl(successfulTracker).mailOpened(1, 1).unsafeRunSync() shouldBe()
    }
  }

  "siteOpened" should {
    "fail if failing to track event" in new TestContext {
      val failingTracker = eventTracker(IO.raiseError(new Exception))

      an[Exception] should be thrownBy EventServiceAlg.impl(failingTracker).siteOpened(1, 1).unsafeRunSync()
    }

    "return unit when tracking is successful" in new TestContext {
      val successfulTracker = eventTracker(IO(()))

      EventServiceAlg.impl(successfulTracker).siteOpened(1, 1).unsafeRunSync() shouldBe()
    }
  }

  "referralLinkOpened" should {
    "fail if failing to track event" in new TestContext {
      val failingTracker = eventTracker(IO.raiseError(new Exception))

      an[Exception] should be thrownBy EventServiceAlg.impl(failingTracker).referralLinkOpened(1, 1).unsafeRunSync()
    }

    "return unit when tracking is successful" in new TestContext {
      val successfulTracker = eventTracker(IO(()))

      EventServiceAlg.impl(successfulTracker).referralLinkOpened(1, 1).unsafeRunSync() shouldBe()
    }
  }

  trait TestContext {
    def eventTracker(response: IO[Unit]): EventTrackerAlg[IO] = new EventTrackerAlg[IO] {
      override def trackEvent(event: UserEvent): IO[Unit] = response

      override def trackCampaignEvent(event: Event.CampaignEvent): IO[Unit] = IO.never

    }
  }

}
