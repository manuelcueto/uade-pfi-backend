package org.cueto.pfi

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, IO, Sync, Timer}
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import fs2.kafka.{AutoOffsetReset, ConsumerSettings, Deserializer, consumerStream}
import io.circe.parser.decode
import org.cueto.pfi.config.KafkaConfig
import org.cueto.pfi.domain.EventReadingResult._
import org.cueto.pfi.domain.Event.{CampaignEvent, UserEvent}
import org.cueto.pfi.domain.EventType.{CampaignDefined, CampaignRunning, SamplingStarted}
import org.cueto.pfi.domain.{
  CampaignId,
  CampaignState,
  CampaignStats,
  CurrentStatus,
  Event,
  EventReadingResult,
  State,
  Topics,
  UserId,
  UserStats
}

import scala.collection.mutable

class EventTracker[F[+_]: Sync](
    inputStream: fs2.Stream[F, Event],
    emailBuddyService: EmailBuddyServiceAlg[F],
    schedulerAlg: SchedulerAlg[F],
    statsService: StatsAlg[F]
) {

  val initialState = Ref[F].of(mutable.HashMap.empty[CampaignId, CampaignState])

  def stream =
    initialState
      .map { refState =>
        inputStream
          .evalScan(refState) {
            case (currentState, record) =>
              for {
                eventReadingResult <- currentState.modify(EventTracker.buildCampaignState(record))
                _ <- eventReadingResult.fold(
                  Sync[F].unit,
                  campaignUpdated => statsService.recordStats(campaignUpdated.campaignStats),
                  sampleFinished =>
                    statsService.recordStats(sampleFinished.campaignStats) >>
                      emailBuddyService.finishSampling(sampleFinished.campaignStats.id),
                  campaignFinished => statsService.recordStats(campaignFinished.campaignStats),
                  timeOutSchedule => {
                    schedulerAlg.scheduleTimeout(timeOutSchedule.campaignId, timeOutSchedule.in)
                  }
                )
              } yield currentState
          }
      }
}

object EventTracker {

  def configure[F[+_]: ConcurrentEffect: Timer: ContextShift](
      config: KafkaConfig,
      emailBuddyService: EmailBuddyServiceAlg[F],
      schedulerAlg: SchedulerAlg[F],
      statsService: StatsAlg[F]
  ) = {

    implicit val campaignEventDeserializer: Deserializer[F, Event] = Deserializer.lift[F, Event] { bytes =>
      new String(bytes).pure[F].flatMap(json => Sync[F].fromEither(decode[Event](json)))
    }

    val campaignConsumerSettings = ConsumerSettings[F, Option[String], Event]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(config.bootstrapServer)
      .withGroupId(config.consumerGroup)

    val stream: fs2.Stream[F, Event] = consumerStream[F]
      .using(campaignConsumerSettings)
      .evalTap(_.subscribeTo(Topics.campaignTrackingTopic, Topics.userTrackingTopic))
      .flatMap(_.stream)
      .map(_.record.value)
    new EventTracker(stream, emailBuddyService, schedulerAlg, statsService)
  }

  def onSamplingStarted(
      currentState: mutable.HashMap[CampaignId, CampaignState],
      campaignId: CampaignId,
      targetUsers: Int,
      timeLimit: Long
  ) = {
    val sideEffect = currentState
      .get(campaignId)
      .fold[EventReadingResult] { // should never happen
//        currentState.update(
//          campaignId,
//          CampaignState(
//            CurrentStatus.Sampling(targetUsers, timeLimit),
//            Map.empty
//          )
//        )
//        ScheduleTimeout(campaignId, timeLimit)
        NoChange
      } {
        case CampaignState(CurrentStatus.Defined(totalUsers), acknowledgedUsers)
            if acknowledgedUsers.size < targetUsers =>
          currentState.update(
            campaignId,
            CampaignState(
              CurrentStatus.Sampling(totalUsers, targetUsers - acknowledgedUsers.size, timeLimit),
              acknowledgedUsers
            )
          )
          ScheduleTimeout(campaignId, timeLimit)
        case CampaignState(CurrentStatus.Defined(totalUsers), acknowledgedUsers)
            if acknowledgedUsers.size == targetUsers =>
          currentState.update(campaignId, CampaignState(CurrentStatus.SamplingComplete(totalUsers), acknowledgedUsers))
          SampleFinished(CampaignStats.fromUserStats(campaignId, totalUsers, acknowledgedUsers.values.toSeq))
        case _ =>
          NoChange

      }
    currentState -> sideEffect
  }

  def onCampaignRunning(
      currentState: mutable.HashMap[CampaignId, CampaignState],
      campaignId: CampaignId,
      target: Int
  ) = {
    currentState
      .get(campaignId)
      .foreach {
        case CampaignState(
              CurrentStatus.SamplingComplete(totalUsers),
              acknowledgedUsers
            ) => //target coming from event is usersLeft, so ack has to include that
          currentState.update(
            campaignId,
            CampaignState(CurrentStatus.Running(totalUsers, target + acknowledgedUsers.size), acknowledgedUsers)
          )
        case _ => ()
      }
    currentState -> NoChange
  }

  def onUserEvent(
      currentState: mutable.HashMap[CampaignId, CampaignState],
      campaignId: CampaignId,
      userId: UserId,
      eventType: State,
      timestamp: Long
  ) = {
    def updateCampaign(totalUsers: Int, users: Map[UserId, UserStats], currentStatus: CurrentStatus): CampaignStats = {
      val stats    = UserStats.statsForEvent(userId, eventType, timestamp, users.get(userId))
      val newUsers = users.updated(userId, stats)
      currentState.update(campaignId, CampaignState(currentStatus, newUsers))
      CampaignStats.fromUserStats(campaignId, totalUsers, newUsers.values.toSeq)
    }

    val sideEffect = currentState
      .get(campaignId)
      .fold[EventReadingResult] {
        currentState.update(
          campaignId,
          CampaignState(
            CurrentStatus.Orphaned,
            Map(userId -> UserStats.statsForEvent(userId, eventType, timestamp, none))
          )
        )
        NoChange
      } {
        case CampaignState(CurrentStatus.Sampling(totalUsers, usersLeft, _), acknowledgedUsers)
            if usersLeft == 1 && !acknowledgedUsers.contains(userId) =>
          SampleFinished(updateCampaign(totalUsers, acknowledgedUsers, CurrentStatus.SamplingComplete(totalUsers)))
        case CampaignState(CurrentStatus.Sampling(totalUsers, usersLeft, timeLimit), acknowledgedUsers) =>
          CampaignUpdated(
            updateCampaign(totalUsers, acknowledgedUsers, CurrentStatus.Sampling(totalUsers, usersLeft - 1, timeLimit))
          )
        case CampaignState(CurrentStatus.Running(totalUsers, usersLeft), acknowledgedUsers)
            if usersLeft == 1 && !acknowledgedUsers.contains(userId) =>
          CampaignFinished(updateCampaign(totalUsers, acknowledgedUsers, CurrentStatus.Completed(totalUsers)))
        case CampaignState(CurrentStatus.Running(totalUsers, usersLeft), acknowledgedUsers) =>
          CampaignUpdated(
            updateCampaign(totalUsers, acknowledgedUsers, CurrentStatus.Running(totalUsers, usersLeft - 1))
          )
        case CampaignState(state @ CurrentStatus.Defined(totalUsers), acknowledgedUsers) =>
          CampaignUpdated(updateCampaign(totalUsers, acknowledgedUsers, state))
        case CampaignState(state @ CurrentStatus.Completed(totalUsers), acknowledgedUsers) =>
          CampaignUpdated(updateCampaign(totalUsers, acknowledgedUsers, state))
        case CampaignState(CurrentStatus.Orphaned, acknowledgedUsers) =>
          currentState.update(
            campaignId,
            CampaignState(
              CurrentStatus.Orphaned,
              acknowledgedUsers
                .updated(userId, UserStats.statsForEvent(userId, eventType, timestamp, acknowledgedUsers.get(userId)))
            )
          )
          NoChange
        case CampaignState(CurrentStatus.SamplingComplete(totalUsers), acknowledgedUsers) =>
          CampaignUpdated(
            updateCampaign(totalUsers, acknowledgedUsers, CurrentStatus.SamplingComplete(totalUsers))
          )
      }
    currentState -> sideEffect

  }

  def onCampaignDefined(
      currentState: mutable.HashMap[CampaignId, CampaignState],
      campaignId: CampaignId,
      totalUsers: Int
  ): (mutable.HashMap[CampaignId, CampaignState], EventReadingResult) = {
    currentState
      .get(campaignId)
      .fold[(mutable.HashMap[CampaignId, CampaignState], EventReadingResult)] {
        currentState.put(campaignId, CampaignState(CurrentStatus.Defined(totalUsers), Map.empty))
        currentState -> CampaignUpdated(CampaignStats(campaignId, totalUsers, 0, 0, 0, 0, 0, 0))
      } {
        case CampaignState(CurrentStatus.Orphaned, acknowledgedUsers) =>
          currentState.update(campaignId, CampaignState(CurrentStatus.Defined(totalUsers), acknowledgedUsers))
          currentState -> CampaignUpdated(
            CampaignStats.fromUserStats(campaignId, totalUsers, acknowledgedUsers.values.toSeq)
          )
        case _ =>
          currentState -> NoChange
      }
  }

  def buildCampaignState(
      record: Event
  ): mutable.HashMap[CampaignId, CampaignState] => (mutable.HashMap[CampaignId, CampaignState], EventReadingResult) =
    (currentState: mutable.HashMap[CampaignId, CampaignState]) => {
      record match {
        case CampaignEvent(campaignId, CampaignDefined(totalUsers)) =>
          onCampaignDefined(currentState, campaignId, totalUsers)
        case CampaignEvent(campaignId, SamplingStarted(targetUsers, timeLimit)) =>
          onSamplingStarted(currentState, campaignId, targetUsers, timeLimit)
        case CampaignEvent(campaignId, CampaignRunning(target)) =>
          onCampaignRunning(currentState, campaignId, target)
        case UserEvent(campaignId, userId, eventType, timestamp) =>
          onUserEvent(currentState, campaignId, userId, eventType, timestamp)
        case Event.TimeLimitReached(campaignId) =>
          val sideEffect = currentState.get(campaignId).fold[EventReadingResult](NoChange) {
            case CampaignState(CurrentStatus.Sampling(totalUsers, _, _), acknowledgedUsers) =>
              currentState
                .update(campaignId, CampaignState(CurrentStatus.SamplingComplete(totalUsers), acknowledgedUsers))
              SampleFinished(CampaignStats.fromUserStats(campaignId, -1, acknowledgedUsers.values.toSeq))
            case _ => NoChange
          }

          currentState -> sideEffect
      }
    }
}
