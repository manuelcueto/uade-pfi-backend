package org.cueto.pfi.domain

import doobie.util.Read
import io.circe.Codec
import io.circe.generic.semiauto._

final case class Personality(
    extraversion: Int,
    agreeableness: Int,
    conscientiousness: Int,
    neuroticism: Int,
    openness: Int
)
// "extraversion","agreeableness","conscientiousness","neuroticism","openness"
object Personality {
  implicit val codec: Codec[Personality] = deriveCodec[Personality]

  implicit val read = Read[Personality]
}
