package org.cueto.pfi

import org.cueto.pfi.domain.CampaignId

sealed trait EventReadingResult extends Product with Serializable

object EventReadingResult {

  case object NoChange extends EventReadingResult

  final case class SampleFinished(campaignId: CampaignId) extends EventReadingResult

  final case class CampaignFinished(campaignId: CampaignId) extends EventReadingResult

  implicit class EventReadingResultOps(val res: EventReadingResult) extends AnyVal {
    def fold[A](noChangeF: => A, sampleFinishedF: SampleFinished => A, campaignFinishedF: CampaignFinished => A): A =
      res match {
        case NoChange => noChangeF
        case a: SampleFinished => sampleFinishedF(a)
        case a: CampaignFinished => campaignFinishedF(a)
      }
  }

}
