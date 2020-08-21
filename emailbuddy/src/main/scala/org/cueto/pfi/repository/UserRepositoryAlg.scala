package org.cueto.pfi.repository

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.instances.option._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import doobie.Fragments
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.cueto.pfi.domain._

trait UserRepositoryAlg[F[+_]] {
  def createNewUser(newUser: NewUser, baseId: Option[BaseId]): F[UserId]
  def getUsers(userIds: List[UserId]): F[List[User]]
  def getUsers(baseId: BaseId): fs2.Stream[F, User]
  def getUser(userId: UserId): F[User]
}

object UserRepositoryAlg {

  def impl[F[+_]: Sync](xa: Transactor[F]) =
    new UserRepositoryAlg[F] {

      override def createNewUser(newUser: NewUser, baseId: Option[BaseId]): F[UserId] = {
        (for {
          maybeUserId <-
            sql"""insert into user
                       |(name, sex, handedness, email, nationality, age, extraversion,
                       | agreeableness, conscientiousness, neuroticism, openness)
                       | values (${newUser.name}, ${newUser.sex}, ${newUser.handedness}, ${newUser.email},
                       | ${newUser.nationality}, ${newUser.age}, ${newUser.personality.extraversion},
                       | ${newUser.personality.agreeableness}, ${newUser.personality.conscientiousness},
                       | ${newUser.personality.neuroticism}, ${newUser.personality.openness})""".stripMargin.update
              .withGeneratedKeys[BaseId]("id")
              .compile
              .toList
              .map(_.headOption)
          _ <- (baseId, maybeUserId).traverseN {
            case (baseId, userId) =>
              sql"insert into user_user_base (user_id, base_id) values ($userId, $baseId)".update.run
          }

        } yield maybeUserId)
          .transact(xa)
          .flatMap(_.fold[F[UserId]](Sync[F].raiseError(UserCreationException(newUser)))(_.pure[F]))

      }

      override def getUsers(userIds: List[UserId]): F[List[User]] = {
        NonEmptyList
          .fromList(userIds)
          .fold[F[List[User]]](Sync[F].raiseError(new AppException("users have to be nonEmpty"))) { ids =>
            (fr"select id, name, sex, handedness, email, nationality, age, extraversion, agreeableness, conscientiousness, neuroticism, openness from user where" ++ Fragments
              .in(fr"id", ids)).query[User].stream.compile.toList.transact(xa)
          }

      }

      override def getUser(userId: UserId): F[User] =
        sql"select id, name, sex, handedness, email, nationality, age, extraversion, agreeableness, conscientiousness, neuroticism, openness from user where id = $userId"
          .query[User]
          .option
          .transact(xa)
          .flatMap(Sync[F].fromOption(_, new AppException(s"user $userId not found")))


      override def getUsers(baseId: BaseId): fs2.Stream[F, User] =
        sql"select u.* from user u inner join user_user_base b on u.id = b.user_id where b.base_id = $baseId"
          .query[User]
          .stream
          .transact(xa)
    }
}
