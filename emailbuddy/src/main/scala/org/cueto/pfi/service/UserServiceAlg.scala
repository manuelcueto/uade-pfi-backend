package org.cueto.pfi.service

import cats.effect.Sync
import org.cueto.pfi.domain.{BaseId, NewUser, Personality, User, UserId}
import org.cueto.pfi.repository.UserRepositoryAlg

trait UserServiceAlg[F[+_]] {
  def createUser(newUser: NewUser, baseId: Option[BaseId]): F[UserId]
  def getUsers(userIds: List[UserId]): F[List[User]]
  def getUsers(baseId: BaseId): fs2.Stream[F, User]
  def getUser(userId: UserId): F[User]
}

object UserServiceAlg {

  def impl[F[+_]: Sync](userRepo: UserRepositoryAlg[F]): UserServiceAlg[F] =
    new UserServiceAlg[F] {

      override def createUser(newUser: NewUser, baseId: Option[BaseId]): F[UserId] =
        userRepo
          .createNewUser(newUser, baseId)

      override def getUsers(userIds: List[UserId]): F[List[User]] = userRepo.getUsers(userIds)

      override def getUser(userId: UserId): F[User] = userRepo.getUser(userId)

      override def getUsers(baseId: BaseId): fs2.Stream[F, User] = userRepo.getUsers(baseId)
    }
}
