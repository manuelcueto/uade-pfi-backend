package org.cueto.pfi.domain

import io.circe.Codec
import io.circe.generic.semiauto._

final case class Personality(
    extraversion: Int,
    agreeableness: Int,
    conscientiousness: Int,
    neuroticism: Int,
    openness: Int
)

object Personality {
  implicit val codec: Codec[Personality] = deriveCodec[Personality]
}
