package org.cueto.pfi.service

import cats.effect.Sync
import cats.instances.either._
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import org.cueto.pfi.domain.{BaseId, NewUser, UserBase, UserBaseSize}
import org.cueto.pfi.repository.UserBaseRepositoryAlg

trait UserBaseServiceAlg[F[+_]] {

  def getUserBase(baseId: BaseId): F[UserBase]
  def baseExists(baseId: BaseId): F[Unit]
  def createBase(name: String, users: List[String]): F[BaseId]
  def updateBase(id: BaseId, name: String, users: List[String]): F[Unit]
  def getBases: F[List[UserBaseSize]]
  def getUserSample(id: BaseId, sample: Int): F[List[String]]
}

object UserBaseServiceAlg {

  def impl[F[+_]: Sync](
      baseRepository: UserBaseRepositoryAlg[F],
      userService: UserServiceAlg[F]
  ): UserBaseServiceAlg[F] =
    new UserBaseServiceAlg[F] {

      override def getUserBase(baseId: BaseId): F[UserBase] =
        baseRepository.getUserBase(baseId)

      override def baseExists(baseId: BaseId): F[Unit] = baseRepository.baseExists(baseId)

      override def createBase(name: String, users: List[String]): F[BaseId] =
        for {
          newUsers <- Sync[F].fromEither(users.drop(1).traverse(NewUser.fromCsv))
          baseId   <- baseRepository.createBase(name)
          userIds <- newUsers.traverse(userService.createUser(_, None))
          _       <- baseRepository.updateBase(baseId, userIds)
        } yield baseId

      override def updateBase(id: BaseId, name: String, users: List[String]): F[Unit] = ???

      override def getUserSample(id: BaseId, sample: Int): F[List[String]] = baseRepository.getSample(id, sample)

      override def getBases: F[List[UserBaseSize]] = baseRepository.getBases
    }
}
