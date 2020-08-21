package org.cueto.pfi.domain

import doobie.util.{Get, Put}
import cats.syntax.either._
import cats.instances.string._

sealed trait UserEmailStatus extends Product with Serializable

object UserEmailStatus {
  case object Sampled extends UserEmailStatus
  case object Unsent  extends UserEmailStatus
  case object Sent    extends UserEmailStatus

  def userEmailStatusMap: String => Either[String, UserEmailStatus] =
    value =>
      value.toLowerCase match {
        case "sampled" => Sampled.asRight
        case "unsent"  => Unsent.asRight
        case "sent"    => Sent.asRight
        case other     => s"invalid value: $other".asLeft
      }

  implicit val userEmailStatusGet: Get[UserEmailStatus] = Get[String].temap(userEmailStatusMap)
  implicit val userEmailStatusPut: Put[UserEmailStatus] = Put[String].contramap(_.toString)
}
