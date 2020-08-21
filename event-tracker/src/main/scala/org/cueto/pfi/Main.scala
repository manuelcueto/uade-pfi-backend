package org.cueto.pfi

import java.util.concurrent.Executors

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import cats.syntax.either._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.cueto.pfi.config.{Config, DatabaseConfig}
import org.cueto.pfi.domain.AppException
import pureconfig.ConfigSource
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext

object Main extends IOApp {

  def database(conf: DatabaseConfig): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](conf.threadPoolSize)
      be <- Blocker[IO]
      xa <- HikariTransactor.newHikariTransactor[IO](
        "com.mysql.jdbc.Driver",
        s"jdbc:mysql://${conf.host}/${conf.database}",
        conf.username,
        conf.password,
        ce,
        be
      )
    } yield xa

  def app(xa: HikariTransactor[IO], config: Config): IO[Unit] =
    for {
      logger <- Slf4jLogger.create[IO]
      apiEc        = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
      schedulerEc  = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
      service      = EmailBuddyServiceAlg.impl[IO](config.api, apiEc)
      scheduler    = SchedulerAlg.impl[IO](config.kafka, schedulerEc)
      statsService = StatsAlg.impl[IO](xa)
      stream <- EventTracker.configure[IO](config.kafka, service, scheduler, statsService).stream
      _      <- stream.compile.drain
    } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      config <- IO.fromEither(ConfigSource.default.load[Config].leftMap(e => new AppException(e.prettyPrint())))
      _      <- database(config.database).use(app(_, config))
    } yield ExitCode.Success
  }
}
