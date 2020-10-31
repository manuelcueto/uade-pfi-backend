package unit

import cats.effect.IO
import cats.syntax.applicative._
import cats.syntax.either._
import org.cueto.pfi.domain.Handedness.Ambidextrous
import org.cueto.pfi.domain.{BaseId, DatabaseException, NewUser, Personality, Sex, User, UserCreationException, UserId}
import org.cueto.pfi.repository.UserRepositoryAlg
import org.cueto.pfi.service.UserServiceAlg
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UserServiceSpec extends AnyWordSpec with Matchers {

  "createUser" should {

    "return an exception when repo fails" in new TestContext {
      val user             = NewUser("name", Sex.Male, Ambidextrous, "email", "argentino", 24, Personality(1, 2, 3, 4, 5))
      val expectedResponse = IO.raiseError(UserCreationException(user))

      an[UserCreationException] should be thrownBy service(expectedResponse).createUser(user, None).unsafeRunSync
    }

    "return unit when user created successfully" in new TestContext {
      val userId = 1
      val user   = NewUser("name", Sex.Male, Ambidextrous, "email", "argentino", 24, Personality(1, 2, 3, 4, 5))

      service(IO(userId)).createUser(user, None).unsafeRunSync shouldBe userId
    }
  }

  trait TestContext {

    def service(createResponse: IO[UserId]): UserServiceAlg[IO] =
      UserServiceAlg.impl(new UserRepositoryAlg[IO] {
        override def createNewUser(newUser: NewUser, baseId: Option[BaseId]): IO[UserId] = createResponse

        override def getUsers(userIds: List[UserId]): IO[List[User]] = IO.never

        override def getUsers(baseId: BaseId): fs2.Stream[IO, User] = fs2.Stream.never[IO]

        override def getUser(userId: UserId): IO[User] = IO.never
      })
  }
}
