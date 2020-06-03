package org.cueto.pfi.domain

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto._
import cats.syntax.functor._
import io.circe.syntax._

sealed trait Event extends Product with Serializable

object Event {

  final case class UserEvent(
      campaignId: CampaignId,
      userId: UserId,
      eventType: State,
      timestamp: Long
  ) extends Event {
    val eventId: String = s"$campaignId-$userId-$timestamp"
  }

  final case class CampaignEvent(campaignId: CampaignId, eventType: EventType) extends Event {
    val eventId: String = s"$campaignId-$eventType"
  }

  implicit val userEventCodec: Codec[UserEvent]         = deriveCodec[UserEvent]
  implicit val campaignEventCodec: Codec[CampaignEvent] = deriveCodec[CampaignEvent]

  implicit val eventDecoder: Decoder[Event] =
    List(Decoder[UserEvent].widen[Event], Decoder[CampaignEvent].widen[Event]).reduceLeft(_ or _)

  implicit val eventEncoder: Encoder[Event] = Encoder.instance[Event] {
    case ev: UserEvent     => ev.asJson
    case ev: CampaignEvent => ev.asJson
  }

}

sealed trait EventType extends Product with Serializable

object EventType {

  final case class SamplingStarted(target: Int) extends EventType {
    override def toString: String = "sampling-started"
  }

  implicit val samplingStartedEncoder: Encoder[SamplingStarted] = deriveEncoder[SamplingStarted]

  implicit val samplingStartedDecoder: Decoder[SamplingStarted] = deriveDecoder[SamplingStarted]

  implicit val encoder: Encoder[EventType] = Encoder.instance[EventType] {
    case ev @ SamplingStarted(_) => ev.asJson
  }

  implicit val decoder: Decoder[EventType] = samplingStartedDecoder.widen[EventType]
  implicit val codec: Codec[EventType]     = Codec.from(decoder, encoder)
}
