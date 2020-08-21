package org.cueto.pfi.domain

import cats.syntax.option._

final case class UserStats(
    userId: UserId,
    timeMailSent: Option[Long],
    timesMailOpened: Int,
    timeOfFirstMailOpened: Option[Long],
    timesSiteOpened: Int,
    timeOfFirstSiteOpened: Option[Long],
    timesCodeUsed: Int,
    timeOfFirstCodeUsed: Option[Long]
)

object UserStats {

  def statsForEvent(userId: UserId, eventType: State, timeStamp: Long, existingStats: Option[UserStats]) = {

    val currentStats = existingStats.fold(UserStats(userId, none, 0, none, 0, none, 0, none))(identity)
    eventType match {
      case State.MailSent =>
        currentStats.copy(timeMailSent = timeStamp.some)
      case State.MailOpened =>
        if (currentStats.timesMailOpened == 0)
          currentStats.copy(timesMailOpened = currentStats.timesMailOpened + 1, timeOfFirstMailOpened = timeStamp.some)
        else
          currentStats.copy(timesMailOpened = currentStats.timesMailOpened + 1)
      case State.SiteOpened =>
        if (currentStats.timesSiteOpened == 0)
          currentStats.copy(timesSiteOpened = currentStats.timesSiteOpened + 1, timeOfFirstSiteOpened = timeStamp.some)
        else
          currentStats.copy(timesSiteOpened = currentStats.timesSiteOpened + 1)
      case State.CodeUsed =>
        if (currentStats.timesCodeUsed == 0)
          currentStats.copy(timesCodeUsed = currentStats.timesCodeUsed + 1, timeOfFirstCodeUsed = timeStamp.some)
        else
          currentStats.copy(timesCodeUsed = currentStats.timesCodeUsed + 1)
    }
  }
}
