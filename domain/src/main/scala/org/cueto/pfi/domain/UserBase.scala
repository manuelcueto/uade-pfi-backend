package org.cueto.pfi.domain

import io.circe.Codec
import io.circe.generic.semiauto._

final case class UserBase(id: BaseId, name: String, users: List[User])

final case class UserBaseSize(id: BaseId, name: String, size: Int)

object UserBase {
  implicit val codec: Codec[UserBase] = deriveCodec
}

object UserBaseSize {

  implicit val codec: Codec[UserBaseSize] = deriveCodec
}
