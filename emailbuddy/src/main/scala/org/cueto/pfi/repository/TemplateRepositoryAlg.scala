package org.cueto.pfi.repository

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.instances.either._
import cats.instances.list._
import cats.syntax.alternative._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import doobie.implicits._
import doobie._
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor
import org.cueto.pfi.domain.{NewTemplate, Template, TemplateId, TemplateNotFound}

trait TemplateRepositoryAlg[F[+_]] {
  def createTemplate(template: NewTemplate): F[Option[TemplateId]]
  def getTemplate(templateId: TemplateId): F[Template]
  def getTemplates: F[List[Template]]
  def getTemplates(templateIds: List[TemplateId]): F[List[Template]]
  def exists(templateIds: TemplateId*): F[Unit]
  def delete(templateId: TemplateId): F[Unit]
}

object TemplateRepositoryAlg {

  def impl[F[+_]: Sync](xa: Transactor[F]): TemplateRepositoryAlg[F] =
    new TemplateRepositoryAlg[F] {

      override def createTemplate(template: NewTemplate): F[Option[TemplateId]] =
        for {
          id <-
            sql"insert into template (name, subject, template) values(${template.name}, ${template.subject}, ${template.text})".update
              .withGeneratedKeys[TemplateId]("id")
              .compile
              .toList
              .transact(xa)
              .map(_.headOption)
        } yield id

      override def getTemplate(templateId: TemplateId): F[Template] =
        for {
          maybeTemplate <-
            sql"select id, name, subject, template from template where id = $templateId"
              .query[Template]
              .option
              .transact(xa)
          template <- Sync[F].fromOption(maybeTemplate, TemplateNotFound(templateId))
        } yield template

      override def exists(templateIds: TemplateId*): F[Unit] = {
        templateIds.toList
          .traverse(id =>
            sql"select id from template where id = $id"
              .query[TemplateId]
              .option
              .map(_.toRight(id))
          )
          .map(_.separate)
          .transact(xa)
          .map {
            case (Nil, _) => ().asRight[TemplateNotFound]
            case (ids, _) => TemplateNotFound(ids: _*).asLeft
          }

      }

      override def getTemplates: F[List[Template]] =
        sql"select id, name, subject, template from template".query[Template].stream.compile.toList.transact(xa)

      override def delete(templateId: TemplateId): F[Unit] =
        sql"delete from template where id = $templateId".update.run.transact(xa).as(())

      override def getTemplates(templateIds: List[TemplateId]): F[List[Template]] =
        NonEmptyList.fromList(templateIds).fold(getTemplates) { ids =>
          (fr"select id, name, subject, template from template where" ++ Fragments
            .in(fr"id", ids))
            .query[Template]
            .stream
            .compile
            .toList
            .transact(xa)
        }
    }
}
