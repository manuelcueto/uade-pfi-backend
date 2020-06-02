package org.cueto.pfi

import io.circe.generic.semiauto._

package object domain {
  type CampaignId = Int
  type BaseId     = Int
  type UserId     = Int
  type TemplateId = Int

  class AppException(msg: String)      extends Throwable(msg)
  class ParsingException(line: String) extends AppException(s"couldn't parse line $line to an user")

  class ParsingFieldException(field: String, message: String)
      extends AppException(s"$field couldn't be parsed because $message")
  class DatabaseException(t: Throwable) extends AppException(t.getMessage)

  final case class CampaignNotFound(campaignId: CampaignId)
      extends AppException(s"no campaign with id $campaignId was found")

  final case class UserBaseNotFound(userBaseId: BaseId)
      extends AppException(s"no userBase with id $userBaseId was found")

  final case class NoBaseForCampaign(campaignId: CampaignId)
      extends AppException(s"no user base found for campaign $campaignId")

  final case class UserBaseCreationException(name: String) extends AppException(s"couldn't create user base with $name")

  final case class TemplateNotFound(templateId: TemplateId*)
      extends AppException(s"no template with id ${templateId.mkString(",")} was found")

  final case class CampaignCreationException(campaign: NewCampaign)
      extends AppException(s"camapign: $campaign could not be created")

  final case class TemplateCreationException(template: NewTemplate)
      extends AppException(s"template: $template could not be created")

  final case class UserCreationException(newUser: NewUser) extends AppException(s"user: $newUser couldNot be created")

}
