package org.cueto.pfi.domain

import io.circe.{Codec, Decoder, Encoder}
import cats.syntax.either._
import cats.instances.string._
import doobie.util.{Get, Put}

sealed trait Handedness extends Product with Serializable

object Handedness {

  case object LeftHanded   extends Handedness
  case object RightHanded  extends Handedness
  case object Ambidextrous extends Handedness

  def handednessMap: String => Either[String, Handedness] =
    value =>
      value.toLowerCase match {
        case "left" | "lefthanded"   => LeftHanded.asRight
        case "right" | "righthanded" => RightHanded.asRight
        case "ambidextrous" | "both" => Ambidextrous.asRight
        case other                   => s"invalid value: $other".asLeft
      }

  implicit val handednessCodec: Codec[Handedness] = {
    Codec.from(
      Decoder.decodeString.emap(handednessMap),
      Encoder.encodeString.contramap[Handedness](_.toString)
    )
  }

  implicit val handednessGet: Get[Handedness] = Get[String].temap(handednessMap)
  implicit val handednessPut: Put[Handedness] = Put[String].contramap(_.toString)

}
