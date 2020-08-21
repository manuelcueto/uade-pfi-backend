package org.cueto.pfi.domain

import io.circe.Codec
import cats.syntax.either._
import io.circe.generic.semiauto._

import scala.util.Try

final case class User(
    id: UserId,
    name: String,
    sex: Sex,
    handedness: Handedness,
    email: String,
    nationality: String,
    age: Int,
    personality: Personality
)

final case class NewUser(
    name: String,
    sex: Sex,
    handedness: Handedness,
    email: String,
    nationality: String,
    age: Int,
    personality: Personality
)

final case class TemplateUserData(id: UserId, name: String, email: String)

object User {
  implicit val codec: Codec[User] = deriveCodec
}

object NewUser {

  def fromCsv(csv: String): Either[AppException, NewUser] = {
    def parseInt: (String, String) => Either[ParsingFieldException, Int] =
      (value, fieldName) =>
        Try(value.toInt).toEither.leftMap(_ => new ParsingFieldException(fieldName, "expected int value"))
    csv.split(',').toList match {
      case name ::
          sex ::
          handedness ::
          email ::
          nationality ::
          age :: answers =>
        for {
          personality <- answers match {
            case a1 ::
                a2 ::
                a3 ::
                a4 ::
                a5 ::
                a6 ::
                a7 ::
                a8 ::
                a9 ::
                a10 ::
                a11 ::
                a12 ::
                a13 ::
                a14 ::
                a15 ::
                a16 ::
                a17 ::
                a18 ::
                a19 ::
                a20 ::
                a21 :: rest =>
              rest match {
                case a22 ::
                    a23 ::
                    a24 ::
                    a25 ::
                    a26 ::
                    a27 ::
                    a28 ::
                    a29 ::
                    a30 ::
                    a31 ::
                    a32 ::
                    a33 ::
                    a34 ::
                    a35 ::
                    a36 ::
                    a37 ::
                    a38 ::
                    a39 ::
                    a40 ::
                    a41 ::
                    a42 ::
                    a43 ::
                    theRest =>
                  theRest match {
                    case a44 :: Nil =>
                      for {
                        a1  <- parseInt(a1, "a1")
                        a2  <- parseInt(a2, "a2")
                        a3  <- parseInt(a3, "a3")
                        a4  <- parseInt(a4, "a4")
                        a5  <- parseInt(a5, "a5")
                        a6  <- parseInt(a6, "a6")
                        a7  <- parseInt(a7, "a7")
                        a8  <- parseInt(a8, "a8")
                        a9  <- parseInt(a9, "a9")
                        a10 <- parseInt(a10, "a10")
                        a11 <- parseInt(a11, "a11")
                        a12 <- parseInt(a12, "a12")
                        a13 <- parseInt(a13, "a13")
                        a14 <- parseInt(a14, "a14")
                        a15 <- parseInt(a15, "a15")
                        a16 <- parseInt(a16, "a16")
                        a17 <- parseInt(a17, "a17")
                        a18 <- parseInt(a18, "a18")
                        a19 <- parseInt(a19, "a19")
                        a20 <- parseInt(a20, "a20")
                        a21 <- parseInt(a21, "a21")
                        a22 <- parseInt(a22, "a22")
                        a23 <- parseInt(a23, "a23")
                        a24 <- parseInt(a24, "a24")
                        a25 <- parseInt(a25, "a25")
                        a26 <- parseInt(a26, "a26")
                        a27 <- parseInt(a27, "a27")
                        a28 <- parseInt(a28, "a28")
                        a29 <- parseInt(a29, "a29")
                        a30 <- parseInt(a30, "a30")
                        a31 <- parseInt(a31, "a31")
                        a32 <- parseInt(a32, "a32")
                        a33 <- parseInt(a33, "a33")
                        a34 <- parseInt(a34, "a34")
                        a35 <- parseInt(a35, "a35")
                        a36 <- parseInt(a36, "a36")
                        a37 <- parseInt(a37, "a37")
                        a38 <- parseInt(a38, "a38")
                        a39 <- parseInt(a39, "a39")
                        a40 <- parseInt(a40, "a40")
                        a41 <- parseInt(a41, "a41")
                        a42 <- parseInt(a42, "a42")
                        a43 <- parseInt(a43, "a43")
                        a44 <- parseInt(a44, "a44")
                        extraversion      = a1 - a6 + a11 + a16 - a21 + a26 - a31 + a36
                        agreeableness     = a7 - a2 - a12 + a17 + a22 - a27 + a32 - a37 + a42
                        conscientiousness = a3 - a8 + a13 - a18 - a23 + a28 + a33 + a38 - a43
                        neuroticism       = a4 - a9 + a14 + a19 - a24 + a29 - a34 + a39
                        openness          = a5 + a10 + a15 + a20 + a25 + a30 - a35 + a40 - a41 + a44
                      } yield Personality(extraversion, agreeableness, conscientiousness, neuroticism, openness)
                    case _ => new ParsingException(csv).asLeft
                  }
                case _ => new ParsingException(csv).asLeft
              }
            case _ => new ParsingException(csv).asLeft
          }
          parsedSex        <- Sex.sexMap(sex).leftMap(new ParsingFieldException("sex", _))
          parsedHandedness <- Handedness.handednessMap(handedness).leftMap(new ParsingFieldException("handedness", _))
          parsedAge        <- parseInt(age, "age")

          user = NewUser(name, parsedSex, parsedHandedness, email, nationality, parsedAge, personality)
        } yield user
      case _ => new ParsingException(csv).asLeft
    }
  }

  implicit val codec: Codec[NewUser] = deriveCodec
}
