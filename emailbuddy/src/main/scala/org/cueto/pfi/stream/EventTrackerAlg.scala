package org.cueto.pfi.stream

import cats.effect.{ConcurrentEffect, ContextShift, Sync}
import cats.syntax.applicative._
import cats.instances.list._
import fs2.kafka._
import io.circe.Encoder
import org.cueto.pfi.config.{Config, KafkaConfig}
import org.cueto.pfi.domain.Event.{CampaignEvent, UserEvent}
import org.cueto.pfi.domain.{Event, Topics}

trait EventTrackerAlg[F[+_]] {
  def trackEvent(event: UserEvent): F[Unit]

  def trackCampaignEvent(event: CampaignEvent): F[Unit]
}

object EventTrackerAlg {

  def impl[F[+_]: Sync: ConcurrentEffect: ContextShift](config: KafkaConfig)(implicit
      encoder: Encoder[Event]
  ) = {

    implicit val eventSerializer: Serializer[F, Event] = Serializer.lift[F, Event] { event =>
      encoder(event).noSpaces.getBytes.pure[F]
    }

    new EventTrackerAlg[F] {

      val producerSettings =
        ProducerSettings[F, String, Event]
          .withBootstrapServers(config.bootstrapServer)

      def send(records: ProducerRecord[String, Event]*): F[Unit] = {
        fs2
          .Stream[F, ProducerRecords[String, Event, Unit]](ProducerRecords(records.toList))
          .through(produce(producerSettings))
          .compile
          .drain
      }
      override def trackEvent(event: UserEvent): F[Unit] =
        send(ProducerRecord(Topics.userTrackingTopic, event.eventId, event))

      override def trackCampaignEvent(event: CampaignEvent): F[Unit] =
        send(ProducerRecord(Topics.campaignTrackingTopic, event.eventId, event))
    }
  }
}
