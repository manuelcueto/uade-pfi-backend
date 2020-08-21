package org.cueto.pfi

import cats.effect.{ConcurrentEffect, Sync}
import cats.syntax.flatMap._
import org.cueto.pfi.config.ApiConfig
import org.cueto.pfi.domain.CampaignId
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._

import scala.concurrent.ExecutionContext

trait EmailBuddyServiceAlg[F[+_]] {

  def finishSampling(campaignId: CampaignId): F[Unit]
}

object EmailBuddyServiceAlg {

  def impl[F[+_]: Sync: ConcurrentEffect](
      apiConfig: ApiConfig,
      ec: ExecutionContext
  ): EmailBuddyServiceAlg[F] =
    new EmailBuddyServiceAlg[F] {

      override def finishSampling(campaignId: CampaignId): F[Unit] = {
        val host     = s"${apiConfig.host}:${apiConfig.port}/api/campaigns"
        val parseUri = Uri.fromString(host).map(_ / campaignId.toString / "finishSampling")
        Sync[F].fromEither(parseUri).flatMap { uri =>
          BlazeClientBuilder[F](ec).resource.use { client =>
            client.expect[Unit](Request[F](method = Method.POST, uri = uri))
          }
        }

      }
    }
}
