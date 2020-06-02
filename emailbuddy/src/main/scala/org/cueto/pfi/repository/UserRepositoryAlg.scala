package org.cueto.pfi.repository

import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.instances.option._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.cueto.pfi.domain.{BaseId, NewUser, UserCreationException, UserId}

trait UserRepositoryAlg[F[+_]] {
  def createNewUser(newUser: NewUser, baseId: Option[BaseId]): F[UserId]
}

object UserRepositoryAlg {

  def impl[F[+_]: Sync](transactor: Transactor[F]) =
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
          .transact(transactor)
          .flatMap(_.fold[F[UserId]](Sync[F].raiseError(UserCreationException(newUser)))(_.pure[F]))

      }
    }
}
