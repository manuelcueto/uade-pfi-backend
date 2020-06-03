package org.cueto.pfi.domain

import io.circe.Codec
import io.circe.generic.semiauto._

final case class Template(id: TemplateId, name: String, text: String) {
  def specialized(name: String, userId: UserId, campaignId: CampaignId): String =
    text
      .replace("{{nombre}}", name)
      .replace("{{userId}}", userId.toString)
      .replace("{{campaignId}}", campaignId.toString)
}

final case class NewTemplate(name: String, text: String)


object Template {
  implicit val codec: Codec[Template] = deriveCodec
}

object NewTemplate {
  implicit val codec: Codec[NewTemplate] = deriveCodec
}