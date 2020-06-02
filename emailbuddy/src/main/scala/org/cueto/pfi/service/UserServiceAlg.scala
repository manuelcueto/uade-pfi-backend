package org.cueto.pfi.service

import cats.effect.Sync
import org.cueto.pfi.domain.{BaseId, NewUser, UserId}
import org.cueto.pfi.repository.UserRepositoryAlg

trait UserServiceAlg[F[+_]] {
  def createUser(newUser: NewUser, baseId: Option[BaseId]): F[UserId]
}

object UserServiceAlg {

  def impl[F[+_]: Sync](userRepo: UserRepositoryAlg[F]): UserServiceAlg[F] =
    new UserServiceAlg[F] {

      override def createUser(newUser: NewUser, baseId: Option[BaseId]): F[UserId] =
        userRepo
          .createNewUser(newUser, baseId)
    }
}
