package org.cueto.pfi.domain

import io.circe.{Codec, Decoder, Encoder}
import cats.instances.string._
import cats.syntax.either._
import doobie.util.{Get, Put}

sealed trait Sex extends Product with Serializable

object Sex {

  case object Male   extends Sex
  case object Female extends Sex

  final case class Other(name: String) extends Sex {
    override def toString: String = name
  }

  def sexMap: String => Either[String, Sex] =
    value =>
      value.toLowerCase match {
        case "male"   => Male.asRight
        case "female" => Female.asRight
        case other    => Other(other).asRight
      }

  implicit val sexCodec: Codec[Sex] = {
    Codec.from(
      Decoder.decodeString.emap(sexMap),
      Encoder.encodeString.contramap[Sex](_.toString)
    )
  }

  implicit val sexPut: Put[Sex] = Put[String].contramap(_.toString)
  implicit val sexGet: Get[Sex] = Get[String].temap(sexMap)
}
