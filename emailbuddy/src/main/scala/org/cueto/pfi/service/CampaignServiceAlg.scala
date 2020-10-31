package org.cueto.pfi.service

import cats.Parallel
import cats.effect.Sync
import cats.instances.list._
import cats.instances.option._
import cats.instances.int._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.syntax.functor._
import cats.syntax.option._
import org.cueto.pfi.domain.{CampaignStatus, _}
import org.cueto.pfi.repository.CampaignsRepositoryAlg

trait CampaignServiceAlg[F[+_]] {
  def findCampaign(campaignId: CampaignId): F[Campaign]
  def createCampaign(newCampaign: NewCampaign): F[CampaignId]
  def getAll: F[List[Campaign]]
  def startSampling(campaignId: CampaignId, samplingParameters: SamplingParameters): F[Unit]
  def updateCampaignStatus(campaignId: CampaignId, status: CampaignStatus): F[Unit]
  def runCampaign(campaignId: CampaignId): F[Unit]
}

object CampaignServiceAlg {

  val addImagePixel: Template => Template = template =>
    template
      .copy(text =
        s""" ${template.text} <a href="http://localhost:3000/landingSite/{{campaignId}}/{{userId}}"> Ingresa aca para ver tu Descuento </a>""" //viene por config (no localhost)
      )

  def impl[F[+_]: Sync: Parallel](
      campaignRepo: CampaignsRepositoryAlg[F],
      userBaseService: UserBaseServiceAlg[F],
      templateService: TemplateServiceAlg[F],
      emailService: EmailServiceAlg[F],
      eventService: EventServiceAlg[F],
      campaignUserService: CampaignUserServiceAlg[F],
      userService: UserServiceAlg[F],
      regressionAlg: RegressionServiceAlg[F]
  ): CampaignServiceAlg[F] =
    new CampaignServiceAlg[F] {

      override def findCampaign(campaignId: CampaignId): F[Campaign] =
        campaignRepo.get(campaignId)

      override def createCampaign(newCampaign: NewCampaign): F[CampaignId] =
        for {
          _               <- userBaseService.baseExists(newCampaign.baseId)
          _               <- templateService.templatesExist(newCampaign.templateIds)
          maybeCampaignId <- campaignRepo.create(newCampaign)
          campaignId      <- Sync[F].fromOption(maybeCampaignId, CampaignCreationException(newCampaign))
          usersSize <-
            userService
              .getUsers(newCampaign.baseId)
              .evalMap(user => campaignUserService.add(campaignId, user.id))
              .map(_ => 1)
              .compile
              .foldMonoid
          _ <- eventService.campaignDefined(campaignId, usersSize)
        } yield campaignId

      override def getAll: F[List[Campaign]] = campaignRepo.getAll

      override def startSampling(campaignId: CampaignId, samplingParameters: SamplingParameters) =
        for {
          userBaseId       <- campaignRepo.campaignBaseId(campaignId)
          templateIds      <- campaignRepo.campaignTemplates(campaignId)
          genericTemplates <- templateService.getTemplates(templateIds)
          templates = genericTemplates.map(addImagePixel)
          users         <- userBaseService.getUserSample(userBaseId, samplingParameters.percentage)
          templateUsers <- assignTemplates(templates, users)
          _             <- templateUsers.traverse { case (template, user) => emailService.sendEmails(user, template, campaignId) }
          _             <- eventService.samplingStarted(campaignId, users.size, samplingParameters.limit)
          _             <- updateCampaignStatus(campaignId, CampaignStatus.Sampling)
          _ <- templateUsers.traverse {
            case (template, user) =>
              campaignUserService.update(campaignId, user.id, UserEmailStatus.Sent, template.id)
          }
          _ <- users.traverse(user => eventService.mailSent(user.id, campaignId))
        } yield ()

      private def assignTemplates(
          templates: Seq[Template],
          users: Seq[TemplateUserData]
      ): F[List[(Template, TemplateUserData)]] = {
        fs2
          .Stream[F, Template](templates: _*)
          .repeat
          .zip(fs2.Stream(users: _*))
          .compile
          .toList
      }

      override def updateCampaignStatus(campaignId: CampaignId, status: CampaignStatus): F[Unit] =
        campaignRepo.updateStatus(campaignId, status)

      private def samplingResults(campaignId: CampaignId): F[List[SamplingResult]] =
        for { //UT:
          reactedUsers    <- campaignUserService.getUsers(campaignId, UserEmailStatus.Sampled)
          notReactedUsers <- campaignUserService.getUsers(campaignId, UserEmailStatus.Sent)
          templateIds     <- campaignRepo.campaignTemplates(campaignId)
          templates       <- templateService.getTemplates(templateIds)
          users = reactedUsers.map { case (id, template) => (id, template, true) } ++ notReactedUsers.map {
            case (id, template) => (id, template, false)
          }
          _ = println(reactedUsers.map(_._2).toSet)
          _ = println(notReactedUsers.map(_._2).toSet)
          _ = println(users.size)
          _ = println(templates)
          maybeSamplingResults = users.traverse {
            case (user, maybeTemplateId, reacted) =>
              maybeTemplateId
                .flatMap(templateId => templates.find(_.id == templateId))
                .map(SamplingResult(user.personality, reacted, _))
          }
          results <-
            Sync[F].fromOption(maybeSamplingResults, new AppException("error when fetching templates for users"))
        } yield results

      override def runCampaign(campaignId: CampaignId): F[Unit] =
        for {
          unsentUserIds <- campaignUserService.getUsers(campaignId, UserEmailStatus.Unsent)
          (userIds, _) = unsentUserIds.unzip
          templateIds      <- campaignRepo.campaignTemplates(campaignId)
          genericTemplates <- templateService.getTemplates(templateIds)
          samplingResults  <- samplingResults(campaignId)
          templateUsers    <- regressionAlg.assignUsers(samplingResults, genericTemplates, userIds)
          _                <- updateCampaignStatus(campaignId, CampaignStatus.Running)
          _ <- templateUsers.toList.flatTraverse {
            case (template, users) =>
              users.traverse(emailService.sendEmails(_, addImagePixel(template), campaignId)) >>
            users.traverse(user => eventService.mailSent(user.id, campaignId))
          }
          _ <- eventService.runCampaign(campaignId, userIds.size)
        } yield ()

    }
}
