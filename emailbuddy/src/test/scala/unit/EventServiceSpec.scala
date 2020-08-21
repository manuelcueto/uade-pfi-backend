package unit

import java.io.File

import cats.effect.IO
import org.cueto.pfi.domain.{CampaignId, Event, TemplateId, User, UserEmailStatus, UserId}
import org.cueto.pfi.domain.Event.UserEvent
import org.cueto.pfi.service.{CampaignUserServiceAlg, EventServiceAlg}
import org.cueto.pfi.stream.EventTrackerAlg
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EventServiceSpec extends AnyWordSpec with Matchers {

  "mailOpened" should {
    "fail if failing to track event" in new TestContext {
      val failingTracker = eventTracker(IO.raiseError(new Exception))

      an[Exception] should be thrownBy EventServiceAlg.impl(failingTracker, campaignUserService).mailOpened(1, 1).unsafeRunSync()
    }

    "return unit when track is successful" in new TestContext {
      val successfulTracker = eventTracker(IO(()))

      EventServiceAlg.impl(successfulTracker, campaignUserService).mailOpened(1, 1).unsafeRunSync() shouldBe()
    }
  }

  "siteOpened" should {
    "fail if failing to track event" in new TestContext {
      val failingTracker = eventTracker(IO.raiseError(new Exception))

      an[Exception] should be thrownBy EventServiceAlg.impl(failingTracker, campaignUserService).siteOpened(1, 1).unsafeRunSync()
    }

    "return unit when tracking is successful" in new TestContext {
      val successfulTracker = eventTracker(IO(()))

      EventServiceAlg.impl(successfulTracker, campaignUserService).siteOpened(1, 1).unsafeRunSync() shouldBe()
    }
  }

  "referralLinkOpened" should {
    "fail if failing to track event" in new TestContext {
      val failingTracker = eventTracker(IO.raiseError(new Exception))

      an[Exception] should be thrownBy EventServiceAlg.impl(failingTracker, campaignUserService).referralLinkOpened(1, 1).unsafeRunSync()
    }

    "return unit when tracking is successful" in new TestContext {
      val successfulTracker = eventTracker(IO(()))

      EventServiceAlg.impl(successfulTracker, campaignUserService).referralLinkOpened(1, 1).unsafeRunSync() shouldBe()
    }
  }

  trait TestContext {
    val campaignUserService = new CampaignUserServiceAlg[IO] {
      override def add(campaignId: CampaignId, userId: UserId): IO[Unit] = IO.unit

      override def update(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus, templateId: TemplateId): IO[Unit] = IO.unit

      override def getUsers(campaignId: CampaignId, userEmailStatus: UserEmailStatus): IO[List[(User, Option[TemplateId])]] = IO(List.empty)

      override def updateStatus(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus): IO[Unit] = IO.unit
    }
    def eventTracker(response: IO[Unit]): EventTrackerAlg[IO] = new EventTrackerAlg[IO] {
      override def trackEvent(event: UserEvent): IO[Unit] = response

      override def trackCampaignEvent(event: Event.CampaignEvent): IO[Unit] = IO.never

    }
  }

}
