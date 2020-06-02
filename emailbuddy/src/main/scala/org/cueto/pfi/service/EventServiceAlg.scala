package org.cueto.pfi.service

import java.time.Instant

import cats.effect.Sync
import cats.syntax.functor._
import org.cueto.pfi.domain.Event.{CampaignEvent, UserEvent}
import org.cueto.pfi.domain.EventType.SamplingStarted
import org.cueto.pfi.domain.{CampaignId, State, UserId}
import org.cueto.pfi.stream.EventTrackerAlg

trait EventServiceAlg[F[+_]] {
  def mailOpened(userId: UserId, campaignId: CampaignId): F[Unit]

  def siteOpened(userId: UserId, campaignId: CampaignId): F[Unit]

  def referralLinkOpened(userId: UserId, campaignId: CampaignId): F[Unit]

  def samplingStarted(campaignId: CampaignId, target: Int): F[Unit]
}

object EventServiceAlg {

  def impl[F[+_] : Sync](eventTracker: EventTrackerAlg[F]): EventServiceAlg[F] =
    new EventServiceAlg[F] {

      override def mailOpened(userId: UserId, campaignId: CampaignId): F[Unit] =
        eventTracker
          .trackEvent(UserEvent(campaignId, userId, State.MailOpened, Instant.now().toEpochMilli))
          .map { _ =>
            println("fasdfkjhalskdfhjklashdfjkh")
            ()
          }

      override def siteOpened(userId: UserId, campaignId: CampaignId): F[Unit] =
        eventTracker.trackEvent(UserEvent(campaignId, userId, State.SiteOpened, Instant.now().toEpochMilli))

      override def referralLinkOpened(userId: UserId, campaignId: CampaignId): F[Unit] =
        eventTracker.trackEvent(UserEvent(campaignId, userId, State.CodeUsed, Instant.now().toEpochMilli))

      override def samplingStarted(campaignId: CampaignId, target: Int): F[Unit] =
        eventTracker.trackCampaignEvent(
          CampaignEvent(campaignId, SamplingStarted(target))
        )
    }
}
