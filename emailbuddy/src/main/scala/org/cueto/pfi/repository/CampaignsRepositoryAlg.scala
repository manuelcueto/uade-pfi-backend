package org.cueto.pfi.repository

import cats.effect.Sync
import cats.instances.option._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicative._
import cats.instances.list._
import cats.syntax.traverse._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import org.cueto.pfi.domain.{CampaignStatus, _}

trait CampaignsRepositoryAlg[F[_]] {
  def get(id: CampaignId): F[Campaign]
  def getAll: F[List[Campaign]]
  def create(newCampaign: NewCampaign): F[Option[CampaignId]]
  def campaignBaseId(campaignId: CampaignId): F[BaseId]
  def campaignTemplates(campaignId: CampaignId): F[List[TemplateId]]
  def updateStatus(campaignId: CampaignId, status: CampaignStatus): F[Unit]
}

object CampaignsRepositoryAlg {

  def impl[F[_]: Sync](xa: Transactor[F]) =
    new CampaignsRepositoryAlg[F] {

      override def get(id: CampaignId): F[Campaign] =
        sql"select id, name, status from campaign where id = $id"
          .query[Campaign]
          .option
          .transact(xa)
          .flatMap(Sync[F].fromOption(_, CampaignNotFound(id)))

      override def getAll: F[List[Campaign]] =
        sql"select id, name, status from campaign"
          .query[Campaign]
          .stream
          .compile
          .toList
          .transact(xa)

      override def create(newCampaign: NewCampaign): F[Option[CampaignId]] = {

        val status: CampaignStatus = CampaignStatus.Started
        val campaignQuery =
          sql"insert into campaign (name, status) values (${newCampaign.name}, $status)".update
            .withGeneratedKeys[CampaignId]("id")
        def campaignBase(campaignId: Int) =
          sql"insert into campaign_base (base_id, campaign_id) values (${newCampaign.baseId}, $campaignId)".update

        def campaignTemplate(campaignId: Int) = {
          val sql = "insert into campaign_template (template_id, campaign_id) values (?, ?)"
          Update[(Int, Int)](sql).updateMany(newCampaign.templateIds.map(_ -> campaignId))
        }
        (for {
          campaignId <- campaignQuery.compile.toList.map(_.headOption)
          _          <- campaignId.traverse(campaignBase(_).run)
          _          <- campaignId.traverse(campaignTemplate)
        } yield campaignId).transact(xa)
      }

      def campaignBaseId(campaignId: CampaignId): F[BaseId] =
        sql"select base_id from campaign_base where campaign_id = $campaignId"
          .query[BaseId]
          .option
          .transact(xa)
          .flatMap(_.fold[F[BaseId]](Sync[F].raiseError(NoBaseForCampaign(campaignId)))(_.pure[F]))

      def campaignTemplates(campaignId: CampaignId): F[List[TemplateId]] =
        sql"select template_id from campaign_template where campaign_id = $campaignId"
          .query[TemplateId]
          .stream
          .compile
          .toList
          .transact(xa)

      override def updateStatus(campaignId: CampaignId, status: CampaignStatus): F[Unit] =
        sql"update campaign set status = $status where id = $campaignId".update.run.transact(xa).as(())

    }
}
