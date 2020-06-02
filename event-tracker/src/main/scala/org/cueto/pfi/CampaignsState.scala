package org.cueto.pfi

import org.cueto.pfi.domain.{CampaignId, UserId}


/// Map[CampaignId, CampaignState]
final case class CampaignState(curentStatus: CurrentStatus, acknowledgedUsers: Set[UserId]) {

}

sealed trait CurrentStatus extends Product with Serializable

object CurrentStatus {

  case class Sampling(usersLeft: Int) extends CurrentStatus

  case object SamplingComplete extends CurrentStatus

  case class Running(usersLeft: Int) extends CurrentStatus

  case object Completed extends CurrentStatus

  case object Defined extends CurrentStatus

}
