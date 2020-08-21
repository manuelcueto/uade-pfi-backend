package org.cueto.pfi.service

import cats.effect.{Async, ContextShift, Sync}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import courier._
import io.chrisdavenport.log4cats.Logger
import javax.mail.internet.InternetAddress
import org.cueto.pfi.config.EmailConfig
import org.cueto.pfi.domain.{CampaignId, Template, TemplateUserData}

import scala.concurrent.ExecutionContext

trait EmailServiceAlg[F[+_]] {
  def sendEmails(userData: TemplateUserData, template: Template, campaignId: CampaignId): F[Unit]
  def sendEmail(email: String, subject: String, body: String): F[Unit]
}

object EmailServiceAlg {

  def impl[F[+_]: Async](
      config: EmailConfig,
      logger: Logger[F]
  )(implicit ec: ExecutionContext, cs: ContextShift[F]): EmailServiceAlg[F] =
    new EmailServiceAlg[F] {
      val mailer = Mailer(config.host, config.port).auth(true).as(config.sender, config.password).startTls(true)()

      def sendEmail(email: String, subject: String, body: String): F[Unit] = {
        Sync[F]
          .catchNonFatal(
            Envelope
              .from(new InternetAddress("uadepfi@gmail.com"))
              .to(new InternetAddress(email))
              .subject(subject)
              .content(
                Multipart().html(
                  s"""<html><body>$body</body></html>"""
                )
              )
          )
          .flatMap(email => Async.fromFuture(Async[F].delay(mailer(email))))
      }.onError {
        case e =>
          logger.error(e)(s"error al enviar mail a $email ; subject: $subject - body: $body")
      }

      override def sendEmails(
          userData: TemplateUserData,
          template: Template,
          campaignId: CampaignId
      ): F[Unit] = {
        val (subject, emailBody) = template.specialized(userData.name, userData.id, campaignId)
        sendEmail(userData.email, subject, emailBody)
      }
    }
}
