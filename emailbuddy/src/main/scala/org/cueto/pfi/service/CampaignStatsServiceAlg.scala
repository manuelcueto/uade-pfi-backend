package org.cueto.pfi.service

import org.cueto.pfi.domain.{CampaignId, CampaignStats}
import org.cueto.pfi.repository.CampaignStatsRepositoryAlg

trait CampaignStatsServiceAlg[F[+_]] {
  def getStats(campaignId: CampaignId): F[CampaignStats]

}

object CampaignStatsServiceAlg {

  def impl[F[+_]](repo: CampaignStatsRepositoryAlg[F]): CampaignStatsServiceAlg[F] =
    new CampaignStatsServiceAlg[F] {

      override def getStats(campaignId: CampaignId): F[CampaignStats] =
        repo.getStats(campaignId)
    }
}
