package org.cueto.pfi

import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp}
import fs2.kafka._
import io.circe.parser._
import org.cueto.pfi.domain.Event.{CampaignEvent, UserEvent}
import org.cueto.pfi.domain.EventType.SamplingStarted
import org.cueto.pfi.domain.{CampaignId, Event, Topics}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {


    implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
    val service = EmailBuddyServiceAlg.impl[IO]
    EventTracker.configure[IO](service).stream.compile.drain.as(ExitCode.Success)

    //    val asdd = Ref[IO].of(1)
    //    val foo = fs2.Stream[IO, Int](1 to 10: _*).scan(asdd) {
    //      case (acc, i) =>
    //        for {
    //          ref <- acc
    //          sideEffect <- ref.modify(currState => (currState + i, s"foo${currState + i}"))
    //          _ = println(sideEffect)
    //        } yield ref
    //    }


    // 2do leo events topic y actualizo state, cuando una campaign cambia de estado, hago post en emailbuddy
    //    val consumerSettings = ConsumerSettings[IO, Option[String], String]
    //      .withAutoOffsetReset(AutoOffsetReset.Earliest)
    //      .withBootstrapServers("localhost:9092")
    //      .withGroupId("event-tracker-events")

    //    val stream = consumerStream[IO].using(consumerSettings).evalTap(_.subscribeTo("test")).flatMap(_.stream)

    //    stream.map { rec =>
    //      println(rec.record.value)
    //    }.compile.drain.as(ExitCode.Success)
  }
}
