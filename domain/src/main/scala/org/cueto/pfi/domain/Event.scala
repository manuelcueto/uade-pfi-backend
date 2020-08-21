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

  final case class TimeLimitReached(campaignId: CampaignId) extends Event {
    val eventId: String = s"time-limit-reached-$campaignId"
  }

  implicit val userEventCodec: Codec[UserEvent]                    = deriveCodec
  implicit val campaignEventCodec: Codec[CampaignEvent]            = deriveCodec
  implicit val timeLimitReachedEventCodec: Codec[TimeLimitReached] = deriveCodec

  implicit val eventDecoder: Decoder[Event] =
    List(Decoder[UserEvent].widen[Event], Decoder[CampaignEvent].widen[Event], Decoder[TimeLimitReached].widen[Event])
      .reduceLeft(_ or _)

  implicit val eventEncoder: Encoder[Event] = Encoder.instance[Event] {
    case ev: UserEvent        => ev.asJson
    case ev: CampaignEvent    => ev.asJson
    case ev: TimeLimitReached => ev.asJson
  }

}

sealed trait EventType extends Product with Serializable

object EventType {

  final case class SamplingStarted(target: Int, limit: Long) extends EventType {
    override def toString: String = "sampling-started"
  }

  implicit val samplingStartedEncoder: Encoder[SamplingStarted] = deriveEncoder[SamplingStarted]

  implicit val samplingStartedDecoder: Decoder[SamplingStarted] = deriveDecoder[SamplingStarted]

  final case class CampaignRunning(target: Int) extends EventType {
    override def toString: String = "campaign-running"
  }

  implicit val campaignRunningEncoder: Encoder[CampaignRunning] = deriveEncoder[CampaignRunning]

  implicit val campaignRunningDecoder: Decoder[CampaignRunning] = deriveDecoder[CampaignRunning]

  final case class CampaignDefined(totalUsers: Int) extends EventType {
    override def toString: String = "campaign-defined"
  }

  implicit val campaignDefinedEncoder: Encoder[CampaignDefined] = deriveEncoder[CampaignDefined]

  implicit val campaignDefinedDecoder: Decoder[CampaignDefined] = deriveDecoder[CampaignDefined]

  implicit val encoder: Encoder[EventType] = Encoder.instance[EventType] {
    case ev @ SamplingStarted(_, _) => ev.asJson
    case ev @ CampaignRunning(_)    => ev.asJson
    case ev @ CampaignDefined(_)    => ev.asJson
  }

  implicit val decoder: Decoder[EventType] =
    List(
      samplingStartedDecoder.widen[EventType],
      campaignRunningDecoder.widen[EventType],
      campaignDefinedDecoder.widen[EventType]
    ).reduceLeft(_ or _)
  implicit val codec: Codec[EventType] = Codec.from(decoder, encoder)
}
