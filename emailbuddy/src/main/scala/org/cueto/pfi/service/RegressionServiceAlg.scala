package org.cueto.pfi.service

import cats.Parallel
import cats.effect.Sync
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.option._
import org.cueto.pfi.domain.{AppException, Personality, SamplingResult, Template, TemplateUserData, User}
import smile.classification._
import smile.regression._
import cats.syntax.parallel._
import cats.syntax.functor._
import cats.syntax.flatMap._
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.data.vector.DoubleVector

trait RegressionServiceAlg[F[+_]] {

  def assignUsers(
      samplingResults: List[SamplingResult],
      allTemplates: List[Template],
      users: List[User]
  ): F[Map[Template, List[TemplateUserData]]]
}

object RegressionServiceAlg {

  def impl[F[+_]: Sync: Parallel] =
    new RegressionServiceAlg[F] {

      def trainModel(x: Array[Array[Double]], y: Array[Double]): F[LinearModel] = {
        println(x)
        val model =
          DataFrame
            .of(x, "extraversion", "agreeableness", "conscientiousness", "neuroticism", "openness", "templateId")
            .merge(DoubleVector.of("openMail", y))
        val formula = Formula.lhs("openMail")
        Sync[F].catchNonFatal(OLS.fit(formula, model))
      }

      def modelInput(personality: Personality, templateId: Int): Array[Double] =
        Array(
          personality.openness,
          personality.neuroticism,
          personality.conscientiousness,
          personality.agreeableness,
          personality.extraversion,
          templateId
        ).map(_.toDouble)

      def predictForUser(
          model: LinearModel,
          user: User,
          templateMap: Map[Template, Int]
      ): F[(Template, User)] = {
        templateMap.toList
          .parTraverse {
            case (template, id) =>
              Sync[F]
                .catchNonFatal(model.predict(modelInput(user.personality, id)))
                .map(_ -> template)
          }
          .map(_.sortBy { case (score, _) => score }.reverse)
          .flatMap(maybeTemplate =>
            Sync[F].fromOption(maybeTemplate.headOption, new AppException("no templates found"))
          )
          .map { case (_, template) => template -> user }
      }

      override def assignUsers(
          samplingResults: List[SamplingResult],
          allTemplates: List[Template],
          users: List[User]
      ): F[Map[Template, List[TemplateUserData]]] = {
        println(s"users : $users")

        val templateMap = allTemplates.zipWithIndex.toMap
        for {
          trainingPop <- Sync[F].fromOption(
            samplingResults
              .traverse { res =>
                templateMap.get(res.template).map { tempId =>
                  (modelInput(res.personality, tempId), if (res.userReacted) 1 else 0)
                }
              },
            new AppException("there was an issue when assigning templates")
          )
          (x, y) = trainingPop.unzip
          model           <- trainModel(x.toArray, y.map(_.toDouble).toArray)
          userPredictions <- users.traverse(predictForUser(model, _, templateMap))
        } yield userPredictions.groupMap { case (template, _) => template } {
          case (_, user) => TemplateUserData(user.id, user.name, user.email)
        }
      }
    }
}
