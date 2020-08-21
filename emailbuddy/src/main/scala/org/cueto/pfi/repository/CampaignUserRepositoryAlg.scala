package org.cueto.pfi.repository

import cats.effect.Sync
import cats.syntax.functor._
import doobie.util.transactor.Transactor
import doobie.implicits._
import org.cueto.pfi.domain.{CampaignId, TemplateId, UserEmailStatus, UserId}

trait CampaignUserRepositoryAlg[F[_]] {
  def add(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus): F[Unit]

  def update(
      campaignId: CampaignId,
      userId: UserId,
      userEmailStatus: UserEmailStatus,
      templateId: TemplateId
  ): F[Unit]

  def updateStatus(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus): F[Unit]

  def getUserIds(campaignId: CampaignId, userEmailStatus: UserEmailStatus): F[List[(UserId, Option[TemplateId])]]
}

object CampaignUserRepositoryAlg {

  def impl[F[_]: Sync](xa: Transactor[F]): CampaignUserRepositoryAlg[F] =
    new CampaignUserRepositoryAlg[F] {

      override def add(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus): F[Unit] =
        sql"insert into campaign_user (campaign_id, user_id, email_status) values($campaignId, $userId, $userEmailStatus)".update.run
          .transact(xa)
          .as(())

      override def update(
          campaignId: CampaignId,
          userId: UserId,
          userEmailStatus: UserEmailStatus,
          templateId: TemplateId
      ): F[Unit] = {
        fr"update campaign_user set email_status = $userEmailStatus, template_id = $templateId where campaign_id = $campaignId and user_id = $userId".update.run
          .transact(xa)
          .as(())
      }

      override def getUserIds(
          campaignId: CampaignId,
          userEmailStatus: UserEmailStatus
      ): F[List[(UserId, Option[TemplateId])]] =
        sql"select user_id, template_id from campaign_user where campaign_id = $campaignId and email_status = $userEmailStatus"
          .query[(UserId, Option[TemplateId])]
          .stream
          .compile
          .toList
          .transact(xa)

      override def updateStatus(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus): F[Unit] =
        fr"update campaign_user set email_status = $userEmailStatus where campaign_id = $campaignId and user_id = $userId".update.run
          .transact(xa)
          .as(())
    }
}
