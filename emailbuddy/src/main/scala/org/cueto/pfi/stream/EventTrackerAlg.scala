package org.cueto.pfi.stream

import cats.effect.{ConcurrentEffect, ContextShift, Sync}
import cats.syntax.applicative._
import fs2.kafka._
import io.circe.Encoder
import org.cueto.pfi.domain.Event.{CampaignEvent, UserEvent}
import org.cueto.pfi.domain.Topics


trait EventTrackerAlg[F[+_]] {
  def trackEvent(event: UserEvent): F[Unit]

  def trackCampaignEvent(event: CampaignEvent): F[Unit]
}

object EventTrackerAlg {

  def impl[F[+_] : Sync : ConcurrentEffect : ContextShift](implicit
                                                           enc: Encoder[UserEvent],
                                                           campaignEnc: Encoder[CampaignEvent]
                                                          ) = {

    implicit val eventSerializer: Serializer[F, UserEvent] = Serializer.lift[F, UserEvent] { event =>
      enc(event).noSpaces.getBytes.pure[F]
    }

    implicit val campaignEventSerializer: Serializer[F, CampaignEvent] = Serializer.lift[F, CampaignEvent] { event =>
      campaignEnc(event).noSpaces.getBytes.pure[F]
    }

    new EventTrackerAlg[F] {

      val producerSettings =
        ProducerSettings[F, String, UserEvent]
          .withBootstrapServers("localhost:9092")

      val campaignProducerSettings =
        ProducerSettings[F, String, CampaignEvent]
          .withBootstrapServers("localhost:9092")

      override def trackEvent(event: UserEvent): F[Unit] = {

        val record = ProducerRecords.one(ProducerRecord("test", event.eventId, event))

        fs2.Stream[F, ProducerRecords[String, UserEvent, Unit]](record).through(produce(producerSettings)).compile.drain
      }

      override def trackCampaignEvent(event: CampaignEvent): F[Unit] = {
        val record = ProducerRecords.one(ProducerRecord(Topics.campaignTrackingTopic, event.eventId, event))
        fs2
          .Stream[F, ProducerRecords[String, CampaignEvent, Unit]](record)
          .through(produce(campaignProducerSettings))
          .compile
          .drain
      }
    }
  }
}
