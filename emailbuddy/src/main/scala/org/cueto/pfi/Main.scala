package org.cueto.pfi

import cats.effect.IO.ioConcurrentEffect
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.cueto.pfi.repository._
import org.cueto.pfi.route.Api
import org.cueto.pfi.service._
import org.cueto.pfi.stream.EventTrackerAlg
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger, _}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object Main extends IOApp {

  def run(args: List[String]) = {

    val foo: Resource[IO, IO[ExitCode]] = for {
      ce     <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      fileEc <- ExecutionContexts.fixedThreadPool[IO](2) // fileEC
      be     <- Blocker[IO] // our blocking EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        "com.mysql.jdbc.Driver",           // driver classname
        "jdbc:mysql://127.0.0.1:3306/pfi", // connect URL
        "root",                            // username
        "password",                        // password
        ce,                                // await connection here
        be                                 // execute JDBC operations here
      )
    } yield {

      for {
        logger <- Slf4jLogger.create[IO]
        methodConfig = CORSConfig(
          anyOrigin = true,
          anyMethod = false,
          allowedMethods = Some(Set("GET", "POST", "DELETE")),
          allowCredentials = true,
          maxAge = 1.day.toSeconds
        )

        campaignRepository = CampaignsRepositoryAlg.impl[IO](xa)
        templateRepository = TemplateRepositoryAlg.impl[IO](xa)
        userRepository     = UserRepositoryAlg.impl[IO](xa)
        userBaseRepository = UserBaseRepositoryAlg.impl[IO](xa)
        userService        = UserServiceAlg.impl[IO](userRepository)
        emailService       = EmailServiceAlg.impl[IO]
        userBaseService    = UserBaseServiceAlg.impl[IO](userBaseRepository, userService)
        templateService    = TemplateServiceAlg.impl[IO](templateRepository)
        eventTracker: EventTrackerAlg[IO] = EventTrackerAlg.impl[IO]
        eventService                           = EventServiceAlg.impl(eventTracker)
        campaignService =
          CampaignServiceAlg.impl[IO](campaignRepository, userBaseService, templateService, emailService, eventService)
        blocker                           = Blocker.liftExecutionContext(fileEc)
        router = CORS(
          Router(
            "/api/campaigns" -> Api.campaignRoutes[IO](campaignService),
            "/api/templates" -> Api.templateRoutes[IO](templateService, logger),
            "/api/users"     -> Api.userRoutes[IO](userService),
            "/api/userBases" -> Api.userBaseRoutes[IO](userBaseService),
            "/api/events"    -> Api.eventsApi(eventService, blocker)
          ),
          methodConfig
        ).orNotFound

        app = Logger.httpApp[IO](logHeaders = true, logBody = true)(router)
        exitCode <-
          BlazeServerBuilder[IO](global)
            .bindHttp(9999, "localhost")
            .withHttpApp(app)
            .serve
            .compile
            .drain
            .as(ExitCode.Success)
      } yield exitCode

    }

    foo.use(identity)
  }
}
