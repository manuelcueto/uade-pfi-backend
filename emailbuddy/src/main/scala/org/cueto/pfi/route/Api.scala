package org.cueto.pfi.route

import cats.effect.{Blocker, ContextShift, IO, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import fs2.text
import io.chrisdavenport.log4cats.Logger
import io.circe.syntax._
import org.cueto.pfi.domain._
import org.cueto.pfi.service._
import org.http4s.{HttpRoutes, MediaType, Request, StaticFile}
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.duration._

object Api { // def routes (recibe todos los services, y un logger, devuelve router con cors

  val baseNameHeader = CaseInsensitiveString("X-BASE-NAME")
  val csvContent     = `Content-Type`(MediaType.text.csv)

  def routes[F[+_]: Sync: ContextShift](
      campaignService: CampaignServiceAlg[F],
      templateService: TemplateServiceAlg[F],
      userService: UserServiceAlg[F],
      userBaseService: UserBaseServiceAlg[F],
      eventService: EventServiceAlg[F],
      campaignStatsService: CampaignStatsServiceAlg[F],
      logger: Logger[F],
      blocker: Blocker
  ) = {
    val methodConfig = CORSConfig(
      anyOrigin = true,
      anyMethod = false,
      allowedMethods = Some(Set("GET", "POST", "DELETE")),
      allowCredentials = true,
      maxAge = 1.day.toSeconds
    )
    CORS(
      Router(
        "/api/campaigns" -> Api.campaignRoutes[F](campaignService),
        "/api/templates" -> Api.templateRoutes[F](templateService, logger),
        "/api/users"     -> Api.userRoutes[F](userService),
        "/api/userBases" -> Api.userBaseRoutes[F](userBaseService),
        "/api/events"    -> Api.eventsApi[F](eventService, blocker),
        "/api/stats" -> Api.statsApi[F](campaignStatsService)
      ),
      methodConfig
    ).orNotFound
  }

  def campaignRoutes[F[+_]: Sync](campaignService: CampaignServiceAlg[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes
      .of[F] {
        case GET -> Root / IntVar(campaignId) =>
          campaignService
            .findCampaign(campaignId)
            .attempt
            .flatMap(_.fold(_ => NotFound(), campaign => Ok(campaign.asJson)))
        case GET -> Root =>
          Ok(campaignService.getAll)
        case req @ POST -> Root =>
          for {
            newCampaign <- req.as[NewCampaign]
            campaignId  <- campaignService.createCampaign(newCampaign).attempt
            response    <- campaignId.fold(_ => InternalServerError(), id => Ok(id))
          } yield response
        case req @ POST -> Root / IntVar(campaignId) / "startSampling" =>
          for {
            samplingParameters <- req.as[SamplingParameters]
            response           <- campaignService.startSampling(campaignId, samplingParameters) >> Ok()
          } yield response
        case POST -> Root / IntVar(campaignId) / "startFullCampaign" => //
          campaignService.runCampaign(campaignId) >> Ok()
        case POST -> Root / IntVar(campaignId) / "finishSampling" =>
          campaignService.updateCampaignStatus(campaignId, CampaignStatus.Sampled) >> Ok()
      }

  }

  def templateRoutes[F[+_]: Sync](templateService: TemplateServiceAlg[F], logger: Logger[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root =>
        for {
          newTemplate <- req.as[NewTemplate]
          templateId  <- templateService.createTemplate(newTemplate).attempt
          response <-
            templateId.fold(e => logger.warn(e)("error when creating template") >> InternalServerError(), Ok(_))
        } yield response
      case GET -> Root =>
        for {
          templates <- templateService.getTemplates.attempt
          response  <- templates.fold(_ => InternalServerError(), Ok(_))
        } yield response
      case DELETE -> Root / IntVar(templateId) =>
        for {
          delete <- templateService.deleteTemplate(templateId).attempt
          response <-
            delete.fold(e => logger.warn(e)("error when deleting template") >> InternalServerError(), _ => NoContent())
        } yield response

    }
  }

  def userRoutes[F[+_]: Sync](userService: UserServiceAlg[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root / IntVar(baseId) =>
        for {
          newUser  <- req.as[NewUser]
          result   <- userService.createUser(newUser, baseId.some).attempt
          response <- result.fold(_ => InternalServerError(), _ => NoContent())
        } yield response
    }
  }

  def userBaseRoutes[F[+_]: Sync](userBase: UserBaseServiceAlg[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] {
      case req @ POST -> Root =>
        for {
          baseName <- baseName(req)
          users    <- extractCsv(req)
          result   <- userBase.createBase(baseName, users).attempt
          response <- result.fold(_ => InternalServerError(), Ok(_))
        } yield response

      case req @ POST -> Root / IntVar(baseId) =>
        for {
          baseName <- baseName(req)
          users    <- extractCsv(req)
          result   <- userBase.updateBase(baseId, baseName, users).attempt
          response <- result.fold(_ => InternalServerError(), _ => NoContent())
        } yield response
      case GET -> Root =>
        userBase.getBases.flatMap(Ok(_))
    }
  }

  def eventsApi[F[+_]: Sync: ContextShift](service: EventServiceAlg[F], blocker: Blocker): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "pixel" / IntVar(campaignId) / IntVar(userId) / "pixel.png" =>
        for {
          _         <- service.mailOpened(userId, campaignId)
          maybeFile <- StaticFile.fromResource("pixel.png", blocker).value
          response  <- maybeFile.fold(NotFound())(_.pure[F])
        } yield response
      case POST -> Root / "siteOpened" / IntVar(campaignId) / IntVar(userId) =>
        service.siteOpened(userId, campaignId) >> NoContent()
      case POST -> Root / "codeUsed" / IntVar(campaignId) / IntVar(userId) =>
        service.referralLinkOpened(userId, campaignId) >> NoContent()
    }
  }

  def statsApi[F[+_]: Sync](service: CampaignStatsServiceAlg[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}

    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / IntVar(campaignId) =>
        service.getStats(campaignId).flatMap(Ok(_))
    }
  }

  def baseName[F[_]: Sync](req: Request[F]): F[String] =
    Sync[F].fromOption(
      req.headers.find(_.name == baseNameHeader).map(_.value),
      new AppException(s"missing header $baseNameHeader")
    )

  def extractCsv[F[_]: Sync](request: Request[F]): F[List[String]] = {
    for {
      _     <- Sync[F].fromOption(request.contentType.filter(_ == csvContent), new AppException("wrong contentType"))
      lines <- request.body.through(text.utf8Decode).through(text.lines).compile.toList
    } yield lines
  }
}
