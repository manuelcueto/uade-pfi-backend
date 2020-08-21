package org.cueto.pfi

import cats.Functor
import cats.effect.Sync
import cats.syntax.functor._
import doobie.util.transactor.Transactor
import doobie.implicits._
import org.cueto.pfi.domain.CampaignStats

trait StatsAlg[F[+_]] {
  def recordStats(campaignStats: CampaignStats): F[Unit]
}

object StatsAlg {

  def impl[F[+_]: Sync: Functor](xa: Transactor[F]): StatsAlg[F] =
    new StatsAlg[F] {

      override def recordStats(campaignStats: CampaignStats): F[Unit] = {
        sql"""INSERT INTO campaign_stats  
             |(campaign_id, total_users, mails_opened, sites_opened, 
             |codes_used,  avg_time_to_open, avg_time_to_site, avg_time_to_use_code) VALUES  
             |(${campaignStats.id}, ${campaignStats.totalUsers},  ${campaignStats.mailsOpened},
             |${campaignStats.sitesOpened},  ${campaignStats.codesUsed}, ${campaignStats.avgTimeToOpen},  
             |${campaignStats.avgTimeToSite} , ${campaignStats.avgTimeToUseCode})  
             |ON DUPLICATE KEY UPDATE campaign_id = ${campaignStats.id}, total_users = ${campaignStats.totalUsers},  
             |mails_opened = ${campaignStats.mailsOpened}, sites_opened = ${campaignStats.sitesOpened} ,  
             |codes_used = ${campaignStats.codesUsed}, avg_time_to_open = ${campaignStats.avgTimeToOpen},  
             |avg_time_to_site = ${campaignStats.avgTimeToSite}, 
             |avg_time_to_use_code = ${campaignStats.avgTimeToUseCode}""".stripMargin.update.run
          .transact(xa)
          .as(())
      }
    }
}
