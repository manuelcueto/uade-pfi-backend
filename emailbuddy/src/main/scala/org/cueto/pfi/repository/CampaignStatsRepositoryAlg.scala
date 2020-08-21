package org.cueto.pfi.repository

import cats.FlatMap
import cats.effect.Sync
import cats.syntax.flatMap._
import doobie.util.transactor.Transactor
import doobie.implicits._
import org.cueto.pfi.domain.{CampaignId, CampaignNotFound, CampaignStats}

trait CampaignStatsRepositoryAlg[F[+_]] {
  def getStats(campaignId: CampaignId): F[CampaignStats]
}

object CampaignStatsRepositoryAlg {

  def impl[F[+_]: Sync: FlatMap](xa: Transactor[F]): CampaignStatsRepositoryAlg[F] =
    new CampaignStatsRepositoryAlg[F] {

      override def getStats(campaignId: CampaignId): F[CampaignStats] =
        sql"select * from campaign_stats where campaign_id = $campaignId"
          .query[CampaignStats]
          .option
          .transact(xa)
          .flatMap(Sync[F].fromOption(_, CampaignNotFound(campaignId)))
    }
}
