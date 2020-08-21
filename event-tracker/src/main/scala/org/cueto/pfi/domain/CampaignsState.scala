package org.cueto.pfi.domain

final case class CampaignState(curentStatus: CurrentStatus, acknowledgedUsers: Map[UserId, UserStats])

sealed trait CurrentStatus extends Product with Serializable

object CurrentStatus {

  final case class Sampling(totalUsers: Int, usersLeft: Int, timeLimit: Long) extends CurrentStatus

  final case class Defined(totalUsers: Int) extends CurrentStatus

  final case class Running(totalUsers: Int, usersLeft: Int) extends CurrentStatus

  final case class SamplingComplete(totalUsers: Int) extends CurrentStatus

  final case class Completed(totalUsers: Int) extends CurrentStatus

  case object Orphaned extends CurrentStatus

}
