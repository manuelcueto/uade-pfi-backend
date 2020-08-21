package org.cueto.pfi.domain

sealed trait EventReadingResult extends Product with Serializable

object EventReadingResult {

  case object NoChange extends EventReadingResult

  final case class CampaignUpdated(campaignStats: CampaignStats) extends EventReadingResult

  final case class SampleFinished(campaignStats: CampaignStats) extends EventReadingResult

  final case class CampaignFinished(campaignStats: CampaignStats) extends EventReadingResult

  final case class ScheduleTimeout(campaignId: CampaignId, in: Long) extends EventReadingResult

  implicit class EventReadingResultOps(val res: EventReadingResult) extends AnyVal {

    def fold[A](
        noChangeF: => A,
        campaignUpdatedF: CampaignUpdated => A,
        sampleFinishedF: SampleFinished => A,
        campaignFinishedF: CampaignFinished => A,
        timeOutF: ScheduleTimeout => A
    ): A =
      res match {
        case NoChange            => noChangeF
        case a: SampleFinished   => sampleFinishedF(a)
        case a: CampaignFinished => campaignFinishedF(a)
        case a: ScheduleTimeout          => timeOutF(a)
        case a: CampaignUpdated => campaignUpdatedF(a)
      }
  }

}
