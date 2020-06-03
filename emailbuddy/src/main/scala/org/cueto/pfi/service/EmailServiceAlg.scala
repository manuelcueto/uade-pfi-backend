package org.cueto.pfi.service

import cats.effect.{ContextShift, IO, Sync}
import courier._
import javax.mail.internet.InternetAddress
import org.cueto.pfi.config.EmailConfig
import org.cueto.pfi.domain.Template

import scala.concurrent.ExecutionContext

trait EmailServiceAlg[F[+_]] {
  def sendEmails(userEmails: List[String], templates: List[Template]): F[Unit]
  def sendEmail: F[Unit]
}

object EmailServiceAlg {

  def impl[F[+_]: Sync](config: EmailConfig)(implicit ec: ExecutionContext, cs: ContextShift[IO]): EmailServiceAlg[F] =
    new EmailServiceAlg[F] {
      val mailer = Mailer(config.host, config.port).auth(true).as(config.sender, config.password).startTls(true)()

      def sendEmail: F[Unit] = {
        val mail =
          Envelope
            .from(new InternetAddress("uadepfi@gmail.com"))
            .to(new InternetAddress("manuel.cueto.c@gmail.com"))
            .subject("tu hermana")
            .content(
              Multipart().html(
                """<html><body><img src="http://localhost:9999/api/events/pixel/1/1/pixel.png" alt="img" /> puto</body></html>"""
              )
            )

        Sync[F].delay(mailer(mail))
      }

      override def sendEmails(userEmails: List[String], templates: List[Template]): F[Unit] = {
        fs2
          .Stream[F, Template](templates: _*)
          .repeat
          .zip(fs2.Stream(userEmails: _*))
          .map {
            case (templ, email) => println(s"$templ: $email")
          }
          .compile
          .drain
      }
    }
}
