package org.cueto.pfi.domain

import cats.instances.string._
import cats.syntax.either._
import doobie.util.{Get, Put}
import io.circe.{Codec, Decoder, Encoder}

sealed trait CampaignStatus extends Product with Serializable

object CampaignStatus {

  case object Started  extends CampaignStatus

  case object Sampling extends CampaignStatus
  case object Sampled  extends CampaignStatus
  case object Running  extends CampaignStatus

  def campaignStatusMap: String => Either[String, CampaignStatus] =
    value =>
      value.toLowerCase match {
        case "started"  => Started.asRight[String]
        case "sampling" => Sampling.asRight[String]
        case "sampled"  => Sampled.asRight[String]
        case "running"  => Running.asRight[String]
        case other      => s"unrecognized status $other".asLeft[CampaignStatus]
      }

  implicit val campaignStatusPut: Put[CampaignStatus] = Put[String].contramap(_.toString)
  implicit val campaignStatusGet: Get[CampaignStatus] = Get[String].temap(campaignStatusMap)

  implicit val campaignStatusCodec: Codec[CampaignStatus] = {
    Codec.from(
      Decoder.decodeString.emap(campaignStatusMap),
      Encoder.encodeString.contramap[CampaignStatus](_.toString)
    )
  }
}
