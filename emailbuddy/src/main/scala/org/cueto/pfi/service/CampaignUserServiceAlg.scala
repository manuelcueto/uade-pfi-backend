package org.cueto.pfi.service

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.instances.list._
import cats.syntax.functor._
import org.cueto.pfi.domain.{CampaignId, TemplateId, User, UserEmailStatus, UserId}
import org.cueto.pfi.repository.CampaignUserRepositoryAlg

trait CampaignUserServiceAlg[F[+_]] {
  def add(campaignId: CampaignId, userId: UserId): F[Unit]

  def update(
      campaignId: CampaignId,
      userId: UserId,
      userEmailStatus: UserEmailStatus,
      templateId: TemplateId
  ): F[Unit]

  def updateStatus(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus): F[Unit]
  def getUsers(campaignId: CampaignId, userEmailStatus: UserEmailStatus): F[List[(User, Option[TemplateId])]]
}

object CampaignUserServiceAlg {

  def impl[F[+_]: Sync](repo: CampaignUserRepositoryAlg[F], userServiceAlg: UserServiceAlg[F]) =
    new CampaignUserServiceAlg[F] {

      override def add(
          campaignId: CampaignId,
          userId: UserId
      ): F[Unit] =
        repo.add(campaignId, userId, UserEmailStatus.Unsent)

      override def update(
          campaignId: CampaignId,
          userId: UserId,
          userEmailStatus: UserEmailStatus,
          templateId: TemplateId
      ): F[Unit] =
        repo.update(campaignId, userId, userEmailStatus, templateId)

      override def getUsers(
          campaignId: CampaignId,
          userEmailStatus: UserEmailStatus
      ): F[List[(User, Option[TemplateId])]] =
        repo.getUserIds(campaignId, userEmailStatus).flatMap { users =>
          users.traverse {
            case (userId, templateId) =>
              userServiceAlg.getUser(userId).map(_ -> templateId)
          }
        }

      override def updateStatus(campaignId: CampaignId, userId: UserId, userEmailStatus: UserEmailStatus): F[Unit] =
        repo.updateStatus(campaignId, userId, userEmailStatus)
    }
}
