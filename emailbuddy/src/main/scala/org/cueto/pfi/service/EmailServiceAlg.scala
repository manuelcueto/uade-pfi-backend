package org.cueto.pfi.service

import cats.effect.Sync
import cats.syntax.applicative._
import org.cueto.pfi.domain.Template

trait EmailServiceAlg[F[+_]] {
  def sendEmails(userEmails: List[String], templates: List[Template]): F[Unit]
}

object EmailServiceAlg {

  def impl[F[+_]: Sync] =
    new EmailServiceAlg[F] {

      override def sendEmails(userEmails: List[String], templates: List[Template]): F[Unit] = {

        fs2
          .Stream(templates: _*)
          .repeat
          .zip(fs2.Stream(userEmails: _*))
          .map {
            case (templ, email) => println(s"$templ: $email")
          }
          .compile
          .drain
          .pure[F]
      }
    }
}
