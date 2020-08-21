package org.cueto.pfi

import cats.effect.{ConcurrentEffect, ContextShift, IO, Sync, Timer}
import cats.syntax.applicative._
import cats.instances.list._
import fs2.kafka._
import io.circe.Encoder
import org.cueto.pfi.config.KafkaConfig
import org.cueto.pfi.domain.Event.TimeLimitReached
import org.cueto.pfi.domain.{CampaignId, Event, Topics}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait SchedulerAlg[F[+_]] {
  def scheduleTimeout(campaignId: CampaignId, in: Long): F[Unit]
}

object SchedulerAlg {

  def impl[F[+_]: ContextShift: ConcurrentEffect:  Sync: Timer](config: KafkaConfig, ec: ExecutionContext)(implicit
                                                                                    encoder: Encoder[Event]
  ) =
    new SchedulerAlg[F] {

      implicit val eventSerializer: Serializer[F, Event] = Serializer.lift[F, Event] { event =>
        encoder(event).noSpaces.getBytes.pure[F]
      }

      val producerSettings =
        ProducerSettings[F, String, Event]
          .withBootstrapServers(config.bootstrapServer)

      def send(in: Long, record: ProducerRecord[String, Event]): F[Unit] = {
        fs2
          .Stream[F, ProducerRecords[String, Event, Unit]](ProducerRecords(List(record)))
          .through(produce(producerSettings))
          .delayBy(in.seconds)
          .compile
          .drain
      }

      override def scheduleTimeout(campaignId: CampaignId, in: Long): F[Unit] = {
        val event = TimeLimitReached(campaignId)
        ContextShift[F].evalOn(ec)(send(in, ProducerRecord(Topics.campaignTrackingTopic, event.eventId, event)))
      }

    }

}
