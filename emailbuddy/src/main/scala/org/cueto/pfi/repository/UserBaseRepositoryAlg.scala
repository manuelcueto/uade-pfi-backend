package org.cueto.pfi.repository

import cats.effect.Sync
import cats.syntax.functor._
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.either._
import cats.syntax.applicative._
import doobie.util.transactor.Transactor
import doobie.implicits._
import doobie.util.update.Update
import org.cueto.pfi.domain.{AppException, BaseId, User, UserBase, UserBaseCreationException, UserBaseNotFound, UserBaseSize, UserId}

trait UserBaseRepositoryAlg[F[+_]] {

  def getUserBase(baseId: BaseId): F[UserBase]

  def baseExists(baseId: BaseId): F[Unit]

  def getBases: F[List[UserBaseSize]]

  def createBase(name: String): F[BaseId]

  def updateBase(id: BaseId, users: List[UserId]): F[Unit]

  def getSample(id: BaseId, sample: Int): F[List[String]]
}

object UserBaseRepositoryAlg {

  def impl[F[+_] : Sync](xa: Transactor[F]) =
    new UserBaseRepositoryAlg[F] {

      override def getUserBase(baseId: BaseId): F[UserBase] =
        sql"select id, name from user_base where id = $baseId"
          .query[(BaseId, String)]
          .option
          .transact(xa)
          .flatMap { maybeRes =>
            maybeRes.fold[F[UserBase]](Sync[F].raiseError(UserBaseNotFound(baseId))) {
              case (id, name) => UserBase(id, name, List.empty).pure[F]
            }
          }

      override def baseExists(baseId: BaseId): F[Unit] = {
        sql"select id from user_base where id = $baseId"
          .query[BaseId]
          .option
          .transact(xa)
          .map(_.fold(new AppException("user not found").asLeft[Unit])(_ => ().asRight[AppException]))
      }

      override def createBase(name: String): F[BaseId] =
        sql"insert into user_base (name) values($name)".update
          .withGeneratedKeys[BaseId]("id")
          .compile
          .toList
          .transact(xa)
          .flatMap(_.headOption.fold[F[BaseId]](Sync[F].raiseError(UserBaseCreationException(name)))(_.pure[F]))

      override def updateBase(id: BaseId, users: List[UserId]): F[Unit] = {
        val sql = "insert into user_user_base (base_id, user_id) values(?, ?)"
        Update[(Int, Int)](sql).updateMany(users.map(id -> _)).transact(xa).as(())
      }

      override def getBases: F[List[UserBaseSize]] =
        sql"select base_id, count(user_id ), b.name from user_user_base u inner join user_base b on u.base_id = b.id group by u.base_id"
          .query[(BaseId, Int, String)]
          .stream
          .compile
          .toList
          .transact(xa)
          .map(_.map { case (id, size, name) => UserBaseSize(id, name, size) })

      override def getSample(id: BaseId, sample: Int): F[List[String]] =
        (for {
          sampleSize <- sql"select count(*) from user_user_base where base_id = $id".query[Int].option
          sampleIds <-
            sql"select u.email from user u  inner join user_user_base b on b.user_id = u.id where b.base_id = $id order by Rand() limit $sampleSize"
              .query[String]
              .stream
              .compile
              .toList
        } yield sampleIds).transact(xa)

    }
}
