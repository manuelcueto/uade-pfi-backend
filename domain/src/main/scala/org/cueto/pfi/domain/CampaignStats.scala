package org.cueto.pfi.domain

import io.circe.Codec
import io.circe.generic.semiauto._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.instances.option._

final case class CampaignStats(
    id: CampaignId,
    totalUsers: Int,
    mailsOpened: Int,
    sitesOpened: Int,
    codesUsed: Int,
    avgTimeToOpen: Long,
    avgTimeToSite: Long,
    avgTimeToUseCode: Long
)

object CampaignStats {

  implicit val codec: Codec[CampaignStats] = deriveCodec

  def fromUserStats(campaignId: CampaignId, totalUsers: Int, stats: Seq[UserStats]): CampaignStats = {
    val mailsOpened = stats.count(_.timesMailOpened > 0)
    val sitesOpened = stats.count(_.timesSiteOpened > 0)
    val codesUsed   = stats.count(_.timesCodeUsed > 0)
    def avgTime(f: UserStats => Option[Long]): Long = {
      val (sum, n) = stats.foldLeft((0L, 0)) {
        case ((sum, n), userStats) =>
          f(userStats) match {
            case Some(time) => (sum + time, n + 1)
            case None       => (sum, n)
          }
      }
      if (n != 0) sum / n else 0
    }
    val avgTimeToOpen    = avgTime(stats => (stats.timeMailSent,stats.timeOfFirstMailOpened).mapN(_ - _))
    val avgTimeToSite    = avgTime(stats => (stats.timeMailSent,stats.timeOfFirstSiteOpened).mapN(_ - _))
    val avgTimeToUseCode = avgTime(stats => (stats.timeMailSent,stats.timeOfFirstCodeUsed).mapN(_ - _))
    CampaignStats(
      campaignId,
      totalUsers,
      mailsOpened,
      sitesOpened,
      codesUsed,
      avgTimeToOpen,
      avgTimeToSite,
      avgTimeToUseCode
    )
  }
}
