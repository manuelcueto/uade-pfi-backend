package org.cueto.pfi.domain

import io.circe.{Codec, Decoder, Encoder}
import cats.syntax.either._
import cats.instances.string._
import doobie.util.{Get, Put}

sealed trait State extends Product with Serializable

object State {
  case object MailSent   extends State
  case object MailOpened extends State
  case object SiteOpened extends State
  case object CodeUsed   extends State

  def stateMap: String => Either[String, State] =
    value =>
      value.toLowerCase match {
        case "mailopened" => MailOpened.asRight
        case "codeused"   => CodeUsed.asRight
        case "siteopened" => SiteOpened.asRight
        case "mailsent"   => MailSent.asRight
        case other        => s"invalid value: $other".asLeft
      }

  implicit val stateCodec: Codec[State] = {
    Codec.from(
      Decoder.decodeString.emap(stateMap),
      Encoder.encodeString.contramap[State](_.toString)
    )
  }

  implicit val stateGet: Get[State] = Get[String].temap(stateMap)
  implicit val statePut: Put[State] = Put[String].contramap(_.toString)
}
