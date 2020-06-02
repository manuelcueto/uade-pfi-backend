package unit

import cats.effect.IO
import cats.scalatest.EitherMatchers
import cats.syntax.applicative._
import cats.syntax.either._
import org.cueto.pfi.domain
import org.cueto.pfi.domain.{AppException, BaseId, NewUser, UserBase, UserBaseSize, UserId}
import org.cueto.pfi.repository.UserBaseRepositoryAlg
import org.cueto.pfi.service.{UserBaseServiceAlg, UserServiceAlg}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UserBaseServiceSpec extends AnyWordSpec with Matchers with EitherMatchers {

  "getUserBase" should {
    "return an exception if repo fails to find base" in new TestContext {
      val service = baseService(getResponse = IO.raiseError(new Exception))

      an[Exception] should be thrownBy service.getUserBase(2).unsafeRunSync()
    }

    "return a base if repo succeeds" in new TestContext {
      val base    = UserBase(2, "name", List.empty)
      val service = baseService(getResponse = IO(base))

      service.getUserBase(2).unsafeRunSync() shouldBe base
    }
  }

  "baseExists" should {
    "return an exception if repo fails to find base" in new TestContext {
      val service = baseService(existsResponse = IO.raiseError(new Exception))

      an[Exception] should be thrownBy service.baseExists(2).unsafeRunSync()
    }

    "return unit if base exists" in new TestContext {
      val service = baseService(existsResponse = IO.unit)

      service.baseExists(2).unsafeRunSync() shouldBe ()
    }
  }

  trait TestContext {

    def baseService(
        getResponse: IO[UserBase] = IO.never,
        existsResponse: IO[Unit] = IO.never,
        createBaseResponse: IO[BaseId] = IO.never,
        updateBaseResponse: IO[Unit] = IO.never,
        getBasesResponse: IO[List[UserBaseSize]] = IO.never,
        userServiceResponse: IO[UserId] = IO(1)
    ) =
      UserBaseServiceAlg.impl(
        new UserBaseRepositoryAlg[IO] {
          override def getUserBase(baseId: BaseId): IO[UserBase] = getResponse

          override def baseExists(baseId: BaseId): IO[Unit] = existsResponse

          override def createBase(name: String): IO[BaseId] = createBaseResponse

          override def updateBase(id: BaseId, users: List[UserId]): IO[Unit] = updateBaseResponse

          override def getBases: IO[List[UserBaseSize]] = getBasesResponse

          override def getSample(id: BaseId, sample: BaseId): IO[List[String]] = IO.never
        },
        new UserServiceAlg[IO] {
          override def createUser(newUser: NewUser, baseId: Option[BaseId]): IO[UserId] = userServiceResponse
        }
      )
  }

}
