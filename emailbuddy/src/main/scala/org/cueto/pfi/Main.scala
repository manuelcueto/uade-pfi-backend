package org.cueto.pfi

import java.util.concurrent.Executors

import cats.effect.IO._
import cats.syntax.either._
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.cueto.pfi.config.{Config, DatabaseConfig}
import org.cueto.pfi.domain.AppException
import org.cueto.pfi.repository._
import org.cueto.pfi.route.Api
import org.cueto.pfi.service._
import org.cueto.pfi.stream.EventTrackerAlg
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger, _}
import pureconfig.ConfigSource
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

object Main extends IOApp {

  val config = IO.fromEither(ConfigSource.default.load[Config].leftMap(e => new AppException(e.prettyPrint())))

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

  def app(xa: HikariTransactor[IO], config: Config)(implicit ex: ExecutionContext): IO[Unit] =
    for {
      logger <- Slf4jLogger.create[IO]
      fileEc                            = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.server.staticFileThreadSize))
      blocker                           = Blocker.liftExecutionContext(fileEc)
      campaignRepository                = CampaignsRepositoryAlg.impl[IO](xa)
      templateRepository                = TemplateRepositoryAlg.impl[IO](xa)
      userRepository                    = UserRepositoryAlg.impl[IO](xa)
      userBaseRepository                = UserBaseRepositoryAlg.impl[IO](xa)
      userService                       = UserServiceAlg.impl[IO](userRepository)
      emailService                      = EmailServiceAlg.impl[IO](config.email)
//      _ <- emailService.sendEmail
      userBaseService                   = UserBaseServiceAlg.impl[IO](userBaseRepository, userService)
      templateService                   = TemplateServiceAlg.impl[IO](templateRepository)
      eventTracker: EventTrackerAlg[IO] = EventTrackerAlg.impl[IO](config.kafka)
      eventService                      = EventServiceAlg.impl(eventTracker)
      campaignService =
        CampaignServiceAlg.impl[IO](campaignRepository, userBaseService, templateService, emailService, eventService)

      router = Api.routes(campaignService, templateService, userService, userBaseService, eventService, logger, blocker)

      app = Logger.httpApp[IO](logHeaders = true, logBody = true)(router)
      exitCode <-
        BlazeServerBuilder[IO](global)
          .bindHttp(config.server.bindPort, config.server.bindUrl)
          .withHttpApp(app)
          .serve
          .compile
          .drain
    } yield exitCode

  def run(args: List[String]) = {
    implicit val asd = global
    for {
      conf <- config
      _    <- database(conf.database).use(app(_, conf))
    } yield ExitCode.Success
  }
}
