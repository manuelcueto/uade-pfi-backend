package org.cueto.pfi

import cats.effect.concurrent.Ref
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.syntax.functor._
import cats.effect.{ConcurrentEffect, ContextShift, IO, Sync, Timer}
import fs2.kafka.{AutoOffsetReset, ConsumerSettings, Deserializer, consumerStream}
import io.circe.parser.decode
import org.cueto.pfi.EventReadingResult._
import org.cueto.pfi.domain.Event.{CampaignEvent, UserEvent}
import org.cueto.pfi.domain.EventType.SamplingStarted
import org.cueto.pfi.domain.{CampaignId, Event, Topics}

import scala.collection.mutable

class EventTracker[F[_] : Sync](inputStream: fs2.Stream[F, Event], emailBuddyService: EmailBuddyServiceAlg[F]) {

  val initialState = Ref[F].of(mutable.HashMap.empty[CampaignId, CampaignState])

  def stream: fs2.Stream[F, F[Ref[F, mutable.HashMap[CampaignId, CampaignState]]]] = inputStream.scan(initialState) {
    case (currentState, record) => for {
      ref <- currentState
      eventReadingResult <- ref.modify(EventTracker.buildCampaignState(record))
      _ <- eventReadingResult.fold(Sync[F].unit, sampleFinished => emailBuddyService.finishSampling(sampleFinished.campaignId), _ => Sync[F].unit)
    } yield ref
  }
}


object EventTracker {
  def configure[F[_] : ConcurrentEffect : Timer : ContextShift](emailBuddyService: EmailBuddyServiceAlg[F]) = {


    implicit val campaignEventDeserializer: Deserializer[F, Event] = Deserializer.lift[F, Event] { bytes =>
      new String(bytes).pure[F].flatMap(json => Sync[F].fromEither(decode[Event](json)))
    }

    val campaignConsumerSettings = ConsumerSettings[F, String, Event].withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("localhost:9092")
      .withGroupId("event-tracker")


    val stream = consumerStream[F]
      .using(campaignConsumerSettings)
      .evalTap(_.subscribeTo(Topics.campaignTrackingTopic, Topics.userTrackingTopic))
      .flatMap(_.stream)
      .map(_.record.value)
    new EventTracker(stream, emailBuddyService)
  }


  def buildCampaignState(record: Event): mutable.HashMap[CampaignId, CampaignState] => (mutable.HashMap[CampaignId, CampaignState], EventReadingResult) =
    (currentState: mutable.HashMap[CampaignId, CampaignState]) => record match {
      case CampaignEvent(campaignId, SamplingStarted(targetUsers)) =>
        val sideEffect = currentState.get(campaignId)
          .fold[EventReadingResult] {
            currentState.update(campaignId, CampaignState(CurrentStatus.Sampling(targetUsers), Set.empty))
            NoChange
          } {
            case CampaignState(CurrentStatus.Defined, acknowledgedUsers) if acknowledgedUsers.size < targetUsers =>
              currentState.update(campaignId, CampaignState(CurrentStatus.Sampling(targetUsers - acknowledgedUsers.size), acknowledgedUsers))
              NoChange
            case CampaignState(CurrentStatus.Defined, acknowledgedUsers) if acknowledgedUsers.size == targetUsers =>
              currentState.update(campaignId, CampaignState(CurrentStatus.SamplingComplete, acknowledgedUsers))
              SampleFinished(campaignId)
            case _ =>
              NoChange

          }
        currentState -> sideEffect
      case UserEvent(campaignId, userId, eventType, timestamp) =>
        val sideEffect = currentState.get(campaignId)
          .fold[EventReadingResult] {
            currentState.update(campaignId, CampaignState(CurrentStatus.Defined, Set(userId)))
            NoChange
          } {
            case CampaignState(CurrentStatus.Sampling(usersLeft), acknowledgedUsers)
              if usersLeft == 1 && !acknowledgedUsers.contains(userId) =>
              currentState.update(campaignId, CampaignState(CurrentStatus.SamplingComplete, acknowledgedUsers + userId))
              SampleFinished(campaignId)
            case CampaignState(CurrentStatus.Sampling(usersLeft), acknowledgedUsers) =>
              currentState.update(campaignId, CampaignState(CurrentStatus.Sampling(usersLeft - 1), acknowledgedUsers + userId))
              NoChange
            case CampaignState(CurrentStatus.Running(usersLeft), acknowledgedUsers)
              if usersLeft == 1 && !acknowledgedUsers.contains(userId) =>
              currentState.update(campaignId, CampaignState(CurrentStatus.Completed, acknowledgedUsers + userId))
              NoChange
            //trigger campaign complete
            case CampaignState(CurrentStatus.Running(usersLeft), acknowledgedUsers) =>
              currentState.update(campaignId, CampaignState(CurrentStatus.Running(usersLeft - 1), acknowledgedUsers + userId))
              NoChange
            case CampaignState(state, acknowledgedUsers) =>
              currentState.update(campaignId, CampaignState(state, acknowledgedUsers + userId))
              NoChange
          }
        currentState -> sideEffect

    }
}