package org.cueto.pfi.domain

import io.circe.Codec
import io.circe.generic.semiauto._


final case class Campaign(
    id: CampaignId,
    name: String,
    status: CampaignStatus
)
final case class NewCampaign(name: String, baseId: BaseId, templateIds: List[TemplateId])



object Campaign {


  implicit val codec: Codec[Campaign] = deriveCodec
}

object NewCampaign {
  implicit val codec: Codec[NewCampaign] = deriveCodec
}
