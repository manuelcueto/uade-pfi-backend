package org.cueto.pfi.service

import java.time.Instant

import cats.effect.Sync
import cats.syntax.flatMap._
import org.cueto.pfi.domain.Event.{CampaignEvent, UserEvent}
import org.cueto.pfi.domain.EventType.{CampaignDefined, CampaignRunning, SamplingStarted}
import org.cueto.pfi.domain.{CampaignId, State, UserEmailStatus, UserId}
import org.cueto.pfi.stream.EventTrackerAlg

trait EventServiceAlg[F[+_]] {
  def mailOpened(userId: UserId, campaignId: CampaignId): F[Unit]
  def mailSent(userId: UserId, campaignId: CampaignId): F[Unit]

  def siteOpened(userId: UserId, campaignId: CampaignId): F[Unit]

  def referralLinkOpened(userId: UserId, campaignId: CampaignId): F[Unit]

  def samplingStarted(campaignId: CampaignId, target: Int, limit: Long): F[Unit]

  def runCampaign(campaignId: CampaignId, target: Int): F[Unit]

  def campaignDefined(campaignId: CampaignId, totalUsers: Int): F[Unit]
}

object EventServiceAlg {

  def impl[F[+_]: Sync](
      eventTracker: EventTrackerAlg[F],
      campaignUserService: CampaignUserServiceAlg[F]
  ): EventServiceAlg[F] =
    new EventServiceAlg[F] {

      override def mailOpened(userId: UserId, campaignId: CampaignId): F[Unit] =
        campaignUserService.updateStatus(campaignId, userId, UserEmailStatus.Sampled) >>
          eventTracker.trackEvent(UserEvent(campaignId, userId, State.MailOpened, Instant.now().toEpochMilli))

      override def siteOpened(userId: UserId, campaignId: CampaignId): F[Unit] =
        eventTracker.trackEvent(UserEvent(campaignId, userId, State.SiteOpened, Instant.now().toEpochMilli))

      override def referralLinkOpened(userId: UserId, campaignId: CampaignId): F[Unit] =
        eventTracker.trackEvent(UserEvent(campaignId, userId, State.CodeUsed, Instant.now().toEpochMilli))

      override def samplingStarted(campaignId: CampaignId, target: Int, limit: Long): F[Unit] =
        eventTracker.trackCampaignEvent(
          CampaignEvent(campaignId, SamplingStarted(target, limit))
        )

      override def mailSent(userId: UserId, campaignId: CampaignId): F[Unit] =
        eventTracker.trackEvent(UserEvent(campaignId, userId, State.MailSent, Instant.now().toEpochMilli))

      override def runCampaign(campaignId: CampaignId, target: Int): F[Unit] =
        eventTracker.trackCampaignEvent(CampaignEvent(campaignId, CampaignRunning(target)))

      override def campaignDefined(campaignId: CampaignId, totalUsers: CampaignId): F[Unit] =
        eventTracker.trackCampaignEvent(CampaignEvent(campaignId, CampaignDefined(totalUsers)))
    }
}
