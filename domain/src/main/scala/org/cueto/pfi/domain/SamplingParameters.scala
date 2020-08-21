package org.cueto.pfi.domain

import io.circe.Codec
import io.circe.generic.semiauto._

final case class SamplingParameters(percentage: Int, limit: Long)

object SamplingParameters {
  implicit val codec: Codec[SamplingParameters] = deriveCodec
}
