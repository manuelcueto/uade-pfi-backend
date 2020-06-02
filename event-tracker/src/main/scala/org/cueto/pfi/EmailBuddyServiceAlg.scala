package org.cueto.pfi

import cats.effect.{ConcurrentEffect, Sync}
import org.cueto.pfi.domain.CampaignId
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._

import scala.concurrent.ExecutionContext

trait EmailBuddyServiceAlg[F[_]] {

  def finishSampling(campaignId: CampaignId): F[Unit]
}


object EmailBuddyServiceAlg {
  def impl[F[_] : Sync : ConcurrentEffect](implicit ec: ExecutionContext): EmailBuddyServiceAlg[F] = new EmailBuddyServiceAlg[F] {
    override def finishSampling(campaignId: CampaignId): F[Unit] = {
      val uri = uri"http://localhost:9999/api/campaigns" / campaignId.toString / "finishSampling"
      BlazeClientBuilder[F](ec).resource.use { client =>
        client.expect[Unit](Request[F](method = Method.POST, uri = uri))
      }
    }
  }
}
