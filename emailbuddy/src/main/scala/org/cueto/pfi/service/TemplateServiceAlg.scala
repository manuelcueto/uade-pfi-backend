package org.cueto.pfi.service

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.cueto.pfi.domain.{NewTemplate, Template, TemplateCreationException, TemplateId}
import org.cueto.pfi.repository.TemplateRepositoryAlg

trait TemplateServiceAlg[F[+_]] {

  def getTemplate(templateId: TemplateId): F[Template]
  def getTemplates: F[List[Template]]
  def getTemplates(templateIds: List[TemplateId]): F[List[Template]]
  def templatesExist(templateIds: List[TemplateId]): F[Unit]
  def createTemplate(newTemplate: NewTemplate): F[TemplateId]
  def deleteTemplate(templateId: TemplateId): F[Unit]
}

object TemplateServiceAlg {

  def impl[F[+_]: Sync](templateRepo: TemplateRepositoryAlg[F]): TemplateServiceAlg[F] =
    new TemplateServiceAlg[F] {

      override def getTemplate(templateId: TemplateId): F[Template] =
        templateRepo.getTemplate(templateId)

      override def templatesExist(templateIds: List[TemplateId]): F[Unit] =
        templateRepo.exists(templateIds: _*)

      override def createTemplate(newTemplate: NewTemplate): F[TemplateId] =
        for {
          maybeTemplateId <- templateRepo.createTemplate(newTemplate)
          templateId      <- Sync[F].fromOption(maybeTemplateId, TemplateCreationException(newTemplate))
        } yield templateId

      override def getTemplates: F[List[Template]] = templateRepo.getTemplates

      override def deleteTemplate(templateId: TemplateId): F[Unit] = templateRepo.delete(templateId)

      override def getTemplates(templateIds: List[TemplateId]): F[List[Template]] = templateRepo.getTemplates(templateIds)
    }
}
