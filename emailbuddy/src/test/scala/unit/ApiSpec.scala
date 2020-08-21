package unit

import cats.effect.{Blocker, ContextShift, IO}
import cats.syntax.applicative._
import cats.syntax.either._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.{Decoder, Encoder}
import org.cueto.pfi.domain.Handedness.Ambidextrous
import org.cueto.pfi.domain._
import org.cueto.pfi.route.Api
import org.cueto.pfi.service._
import org.http4s.Method._
import org.http4s._
import org.http4s.circe._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class ApiSpec extends AnyWordSpec with Matchers {

  "templateRoutes" should {

    "fail for invalid request body" in new TestContext {

      val req = post("""{"foo": "bar"}""", uri"/")

      an[InvalidMessageBodyFailure] shouldBe thrownBy(
        runRequest(Api.templateRoutes(templateService(createTemplateResponse = IO(1)), logger), req)
      )
    }

    "return 500 if template couldn't be created" in new TestContext {
      val req = post(NewTemplate("template", "subject", "foo"), uri"/")

      runRequest(
        Api.templateRoutes(templateService(createTemplateResponse = IO.raiseError(new Exception)), logger),
        req
      ).status shouldBe Status.InternalServerError
    }

    "return the id of a successfully created template" in new TestContext {
      val newTemplate = NewTemplate("template", "subject", "<h1>template</h1>")
      val templateId  = 1

      val req =
        post(newTemplate, uri"/")

      val response =
        runRequest(Api.templateRoutes(templateService(createTemplateResponse = IO(templateId)), logger), req)
      response.status shouldBe Status.Ok
      bodyAs[TemplateId](response) shouldBe templateId
    }

    "return 500 if templates cannot be retrieved" in new TestContext {
      val req = get(uri"/")

      val service = templateService(getTemplatesResponse = IO.raiseError(new Exception))
      runRequest(Api.templateRoutes(service, logger), req).status shouldBe Status.InternalServerError
    }

    "return templates if the service works" in new TestContext {
      val req       = get(uri"/")
      val templates = List(Template(1, "asd", "subject", "fafa"))
      val service   = templateService(getTemplatesResponse = IO(templates))

      val response = runRequest(Api.templateRoutes(service, logger), req)

      response.status shouldBe Status.Ok
      bodyAs[List[Template]](response) shouldBe templates
    }

    "return 500 if template cannot be deleted" in new TestContext {
      val req = delete(uri"/1")

      val service = templateService(deleteTemplateResponse = IO.raiseError(new Exception))
      runRequest(Api.templateRoutes(service, logger), req).status shouldBe Status.InternalServerError
    }

    "return 204 if template gets deleted successfully" in new TestContext {
      val req     = delete(uri"/1")
      val service = templateService(deleteTemplateResponse = IO.unit)
      runRequest(Api.templateRoutes(service, logger), req).status shouldBe Status.NoContent
    }

  }

  "campaignRoutes" should {
    "return all available campaigns" in new TestContext {
      val req              = get(uri"/")
      val expectedResponse = List.empty
      val response         = runRequest(Api.campaignRoutes(campaignService(expectedResponse)), req)

      response.status shouldBe Status.Ok
      bodyAs[List[Campaign]](response) shouldBe expectedResponse
    }

    "return a campaign when found" in new TestContext {
      val campaignId       = 2
      val req              = get(uri"/2")
      val expectedResponse = Campaign(campaignId, "name", CampaignStatus.Started)
      val response =
        runRequest(Api.campaignRoutes(campaignService(findCampaignResponse = IO(expectedResponse))), req)
      response.status shouldBe Status.Ok
      bodyAs[Campaign](response) shouldBe expectedResponse
    }

    "return not found when campaign is not found" in new TestContext {
      val campaignId       = 2
      val req              = get(uri"/2")
      val expectedResponse = Campaign(campaignId, "name", CampaignStatus.Started)
      val response =
        runRequest(
          Api.campaignRoutes(campaignService(findCampaignResponse = IO.raiseError(CampaignNotFound(campaignId)))),
          req
        )
      response.status shouldBe Status.NotFound
    }

    "fail for invalid request body" in new TestContext {
      val req = post("""{"foo": "bar"}""", uri"/")

      an[InvalidMessageBodyFailure] shouldBe thrownBy(runRequest(Api.campaignRoutes(campaignService()), req))
    }

    "return 500 if campaign couldn't be created" in new TestContext {
      val newCampaign = NewCampaign("name", 1, List(1, 2, 3))
      val campaign    = Campaign(1, newCampaign.name, CampaignStatus.Started)
      val req         = post(newCampaign, uri"/")

      val response =
        runRequest(Api.campaignRoutes(campaignService(createCampaignResponse = IO.raiseError(new AppException("poo")))), req)
      response.status shouldBe Status.InternalServerError
    }

    "return the id of a successfully created campaign" in new TestContext {
      val campaignId  = 1
      val newCampaign = NewCampaign("name", campaignId, List(1, 2, 3))
      val req         = post(newCampaign, uri"/")

      val response =
        runRequest(Api.campaignRoutes(campaignService(createCampaignResponse = IO(campaignId))), req)
      response.status shouldBe Status.Ok
      bodyAs[CampaignId](response) shouldBe campaignId
    }
  }

  "userRoutes" should {

    "fail for invalid request body" in new TestContext {
      val req = post("""{"foo": "bar"}""", uri"/1")

      an[InvalidMessageBodyFailure] shouldBe thrownBy(runRequest(Api.userRoutes(userService(IO(1))), req))
    }

    "return 500 if user couldn't be created" in new TestContext {
      val newUser  = NewUser("name", Sex.Male, Ambidextrous, "email", "jew", 24, Personality(1, 2, 3, 4, 5))
      val req      = post(newUser, uri"/1")
      val response = runRequest(Api.userRoutes(userService(IO.raiseError(UserCreationException(newUser)))), req)
      response.status shouldBe Status.InternalServerError
    }

    "return noContent when user created successfully" in new TestContext {
      val newUser  = NewUser("name", Sex.Male, Ambidextrous, "email", "jew", 24, Personality(1, 2, 3, 4, 5))
      val req      = post(newUser, uri"/1")
      val response = runRequest(Api.userRoutes(userService(IO(1))), req)
      response.status shouldBe Status.NoContent
    }
  }

  "userBaseRoutes" should {
    "return 500 if userBase couldn't be created" in new TestContext {
      val body = fs2.Stream[IO, Byte]("1,2,3\n1,2,3".getBytes.toIndexedSeq: _*)
      val req =
        Request(method = POST, uri = uri"/")
          .withBodyStream(body)
          .withHeaders(
            Headers.of(Header(Api.baseNameHeader.toString(), "foo"), `Content-Type`(MediaType.text.csv))
          )
      val userBaseAlg = userBaseService(createResponse = IO.raiseError(new Exception))
      val response    = runRequest(Api.userBaseRoutes(userBaseAlg), req)
      response.status shouldBe Status.InternalServerError
    }

    "return baseId if userBase was created" in new TestContext {
      val body   = fs2.Stream[IO, Byte]("1,2,3\n1,2,3".getBytes.toIndexedSeq: _*)
      val baseId = 2
      val req =
        Request(method = POST, uri = uri"/")
          .withBodyStream(body)
          .withHeaders(
            Headers.of(Header(Api.baseNameHeader.toString(), "foo"), `Content-Type`(MediaType.text.csv))
          )
      val userBaseAlg = userBaseService(createResponse = IO(baseId))
      val response    = runRequest(Api.userBaseRoutes(userBaseAlg), req)
      response.status shouldBe Status.Ok
      bodyAs[BaseId](response) shouldBe baseId
    }

    "return 500 if userBase couldn't be updated" in new TestContext {
      val body = fs2.Stream[IO, Byte]("1,2,3\n1,2,3".getBytes.toIndexedSeq: _*)
      val req =
        Request(method = POST, uri = uri"/2")
          .withBodyStream(body)
          .withHeaders(
            Headers.of(Header(Api.baseNameHeader.toString(), "foo"), `Content-Type`(MediaType.text.csv))
          )
      val userBaseAlg = userBaseService(updateResponse = IO.raiseError(new Exception))
      val response    = runRequest(Api.userBaseRoutes(userBaseAlg), req)
      response.status shouldBe Status.InternalServerError
    }

    "return NoContent if userBase was updated" in new TestContext {
      val body = fs2.Stream[IO, Byte]("1,2,3\n1,2,3".getBytes.toIndexedSeq: _*)
      val req =
        Request(method = POST, uri = uri"/2")
          .withBodyStream(body)
          .withHeaders(
            Headers.of(Header(Api.baseNameHeader.toString(), "foo"), `Content-Type`(MediaType.text.csv))
          )
      val userBaseAlg = userBaseService(updateResponse = IO.unit)
      val response    = runRequest(Api.userBaseRoutes(userBaseAlg), req)
      response.status shouldBe Status.NoContent

    }
  }

  "eventRoutes" should {

    val blocker                       = Blocker.liftExecutionContext(ExecutionContext.global)
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    "fail to track pixel when service fails" in new TestContext {
      val req            = Request[IO](method = GET, uri = uri"/pixel/1/2/pixel.png")
      val failingService = eventService(mailOpenedResponse = IO.raiseError(new Exception))
      an[Exception] should be thrownBy runRequest(Api.eventsApi(failingService, blocker), req)
    }
    // missing happy path tests
    "fail to track siteOpened when service fails" in new TestContext {
      val req            = Request[IO](method = POST, uri = uri"/siteOpened/1/2")
      val failingService = eventService(siteOpenedResponse = IO.raiseError(new Exception))
      an[Exception] should be thrownBy runRequest(Api.eventsApi(failingService, blocker), req)
    }
    "fail to track codeUsed when service fails" in new TestContext {
      val req            = Request[IO](method = POST, uri = uri"/codeUsed/1/2")
      val failingService = eventService(referralLinkOpenedResponse = IO.raiseError(new Exception))
      an[Exception] should be thrownBy runRequest(Api.eventsApi(failingService, blocker), req)
    }
  }

  "baseName" should {
    "return the baseName if header is present" in {
      val baseName = "foo"
      val req      = Request[IO](headers = Headers.of(Header(Api.baseNameHeader.value, baseName)))
      Api.baseName(req).unsafeRunSync() shouldBe baseName
    }

    "fail when header is not present" in {
      an[AppException] should be thrownBy Api.baseName(Request[IO]()).unsafeRunSync()
    }
  }

  "extractCsv" should {
    val csvFile = List("1,2,3", "1,2,3")
    def req(mediaType: MediaType) =
      Request[IO](
        headers = Headers.of(`Content-Type`(mediaType)),
        body = fs2.Stream[IO, Byte](csvFile.mkString("\n").getBytes.toIndexedSeq: _*)
      )

    "fail if request has different contentType" in {
      an[AppException] should be thrownBy Api.extractCsv(req(MediaType.application.json)).unsafeRunSync()
    }

    "extract the Csv file as a List if the request is valid" in {
      Api.extractCsv(req(MediaType.text.csv)).unsafeRunSync() shouldBe csvFile
    }
  }

  trait TestContext {

    val logger = Slf4jLogger.getLogger[IO]

    def post[T: Encoder](entity: T, uri: Uri): Request[IO] =
      Request[IO](method = POST, uri = uri, body = jsonEncoderOf[IO, T].toEntity(entity).body)

    def get(uri: Uri): Request[IO] = Request[IO](uri = uri)

    def delete(uri: Uri): Request[IO] = Request[IO](uri = uri, method = Method.DELETE)

    def runRequest(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] =
      routes.orNotFound.run(request).unsafeRunSync()

    def bodyAs[T: Decoder](response: Response[IO]): T = {
      implicit val d = jsonOf[IO, T]
      response.as[T].unsafeRunSync()
    }

    def campaignService(
        getAllResponse: List[Campaign] = List.empty,
        findCampaignResponse: IO[Campaign] = IO.never,
        createCampaignResponse: IO[CampaignId] = IO.never
    ): CampaignServiceAlg[IO] =
      new CampaignServiceAlg[IO] {

        override def findCampaign(campaignId: CampaignId): IO[Campaign] =
          findCampaignResponse

        override def createCampaign(newCampaign: NewCampaign): IO[CampaignId] =
          createCampaignResponse

        override def getAll: IO[List[Campaign]] = getAllResponse.pure[IO]

        override def startSampling(campaignId: CampaignId, samplingParameters: SamplingParameters): IO[Unit] =
          IO.never

        override def updateCampaignStatus(campaignId: CampaignId, status: CampaignStatus): IO[Unit] = IO.never

        override def runCampaign(campaignId: CampaignId): IO[Unit] = IO.never
      }

    def templateService(
        getTemplateResponse: IO[Template] = IO.never,
        templatesExistResponse: IO[Unit] = IO.never,
        createTemplateResponse: IO[TemplateId] = IO.never,
        getTemplatesResponse: IO[List[Template]] = IO.never,
        deleteTemplateResponse: IO[Unit] = IO.never
    ): TemplateServiceAlg[IO] =
      new TemplateServiceAlg[IO] {

        override def getTemplate(templateId: TemplateId): IO[Template] =
          getTemplateResponse

        override def templatesExist(templateIds: List[TemplateId]): IO[Unit] =
          templatesExistResponse

        override def createTemplate(newTemplate: NewTemplate): IO[TemplateId] =
          createTemplateResponse

        override def getTemplates: IO[List[Template]] = getTemplatesResponse

        override def deleteTemplate(templateId: TemplateId): IO[Unit] = deleteTemplateResponse

        override def getTemplates(templateIds: List[TemplateId]): IO[List[Template]] = IO.never
      }

    def userService(createResponse: IO[UserId]): UserServiceAlg[IO] =
      new UserServiceAlg[IO] {
        override def createUser(newUser: NewUser, baseId: Option[BaseId]): IO[UserId] = createResponse

        override def getUsers(userIds: List[UserId]): IO[List[User]] = IO.never

        override def getUsers(baseId: BaseId): fs2.Stream[IO, User] = fs2.Stream.never[IO]

        override def getUser(userId: UserId): IO[User] = IO.never
      }

    def userBaseService(
        createResponse: IO[BaseId] = IO.never,
        updateResponse: IO[Unit] = IO.never
    ): UserBaseServiceAlg[IO] =
      new UserBaseServiceAlg[IO] {
        override def getUserBase(baseId: BaseId): IO[UserBase] = IO.never

        override def baseExists(baseId: BaseId): IO[Unit] = IO.never

        override def createBase(name: String, users: List[String]): IO[BaseId] =
          createResponse

        override def updateBase(id: BaseId, name: String, users: List[String]): IO[Unit] =
          updateResponse

        override def getUserSample(id: BaseId, sample: Int): IO[List[TemplateUserData]] = IO.never

        override def getBases: IO[List[UserBaseSize]] = IO.never
      }

    def eventService(
        mailOpenedResponse: IO[Unit] = IO.never,
        siteOpenedResponse: IO[Unit] = IO.never,
        referralLinkOpenedResponse: IO[Unit] = IO.never
    ): EventServiceAlg[IO] =
      new EventServiceAlg[IO] {

        override def siteOpened(userId: UserId, campaignId: CampaignId): IO[Unit] = siteOpenedResponse

        override def referralLinkOpened(userId: UserId, campaignId: CampaignId): IO[Unit] = referralLinkOpenedResponse

        override def mailOpened(userId: UserId, campaignId: CampaignId): IO[Unit] = mailOpenedResponse

        override def samplingStarted(campaignId: CampaignId, target: UserId, limit: Long): IO[Unit] = IO.never

        override def runCampaign(campaignId: CampaignId, target: UserId): IO[Unit] = IO.never

        override def campaignDefined(campaignId: CampaignId, totalUsers: UserId): IO[Unit] = IO.never

        override def mailSent(userId: UserId, campaignId: CampaignId): IO[Unit] = IO.never
      }
  }

}
