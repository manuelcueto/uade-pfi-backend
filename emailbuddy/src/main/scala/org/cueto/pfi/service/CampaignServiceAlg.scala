package org.cueto.pfi.service

import cats.data.EitherT
import cats.data.EitherT._
import cats.effect.{IO, Sync}
import cats.effect.concurrent.Ref
import cats.syntax.applicative._
import cats.syntax.either._
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import org.cueto.pfi.domain._
import org.cueto.pfi.repository.CampaignsRepositoryAlg

trait CampaignServiceAlg[F[+_]] {
  def findCampaign(campaignId: CampaignId): F[Either[CampaignNotFound, Campaign]]
  def createCampaign(newCampaign: NewCampaign): F[Either[AppException, CampaignId]]
  def getAll: F[List[Campaign]]
  def startCampaign(campaignId: CampaignId): F[Unit]
  def startSampling(campaignId: CampaignId, samplingRatio: Int): F[Unit]
}

object CampaignServiceAlg {

  def impl[F[+_]: Sync](
      campaignRepo: CampaignsRepositoryAlg[F],
      userBaseService: UserBaseServiceAlg[F],
      templateService: TemplateServiceAlg[F],
      emailService: EmailServiceAlg[F],
      eventService: EventServiceAlg[F]
  ): CampaignServiceAlg[F] =
    new CampaignServiceAlg[F] {

      override def findCampaign(campaignId: CampaignId): F[Either[CampaignNotFound, Campaign]] =
        campaignRepo.get(campaignId)

      override def createCampaign(newCampaign: NewCampaign): F[Either[AppException, CampaignId]] =
        (for {
          _ <- EitherT.liftF(userBaseService.baseExists(newCampaign.baseId))
          _ <- EitherT.liftF(templateService.templatesExist(newCampaign.templateIds))
          campaignId <-
            EitherT.fromOptionF(campaignRepo.create(newCampaign), CampaignCreationException(newCampaign): AppException)
        } yield campaignId).value

      override def getAll: F[List[Campaign]] = campaignRepo.getAll

      override def startCampaign(campaignId: CampaignId): F[Unit] = ().pure[F]

      override def startSampling(campaignId: CampaignId, samplingRatio: Int) =
        for {
          userBaseId  <- campaignRepo.campaignBaseId(campaignId)
          templateIds <- campaignRepo.campaignTemplates(campaignId)
          templates   <- templateService.getTemplates(templateIds)
          users       <- userBaseService.getUserSample(userBaseId, samplingRatio) //return the whole user instead
          _           <- emailService.sendEmails(users, templates)
//          _           <- eventService.samplingStarted(campaignId, users.size)
        } yield ()
    }
}
