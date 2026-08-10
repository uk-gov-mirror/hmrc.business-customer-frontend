/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.nonUKReg

import config.ApplicationConfig
import connectors.{BackLinkCacheConnector, BusinessRegCacheConnector}
import controllers.auth.AuthActions
import controllers.{BackLinkController, BusinessVerificationController}
import forms.BusinessRegistrationForms._
import javax.inject.{Inject, Provider}
import models.PaySAQuestion
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.BusinessCustomerConstants.PaySaDetailsId

import scala.concurrent.ExecutionContext

class PaySAQuestionController @Inject()(val authConnector: AuthConnector,
                                        val backLinkCacheConnector: BackLinkCacheConnector,
                                        config: ApplicationConfig,
                                        template: views.html.nonUkReg.paySAQuestion,
                                        businessRegController: BusinessRegController,
                                        mcc: MessagesControllerComponents,
                                        businessVerificationController: Provider[BusinessVerificationController],
                                        businessRegistrationCache: BusinessRegCacheConnector)
  extends FrontendController(mcc) with AuthActions with BackLinkController {

  implicit val appConfig: ApplicationConfig = config
  implicit val executionContext: ExecutionContext = mcc.executionContext
  val controllerId: String = "PaySAQuestionController"

  def view(service: String): Action[AnyContent] = Action.async { implicit request =>
    authorisedFor(service) { implicit authContext =>
      if (authContext.isAgent) {
        forwardBackLinkToNextPage(businessRegController.controllerId, controllers.nonUKReg.routes.BusinessRegController.register(service, businessType = "NUK"))
      } else {
        for {
          backLink <- currentBackLink
          savedPaySa <- businessRegistrationCache.fetchAndGetCachedDetails[PaySAQuestion](PaySaDetailsId)
        } yield
          Ok(template(paySAQuestionForm.fill(savedPaySa.getOrElse(PaySAQuestion())), service, backLink))
      }
    }
  }

  def continue(service: String): Action[AnyContent] = Action.async { implicit request =>
    authorisedFor(service) { implicit authContext =>
      paySAQuestionForm.bindFromRequest().fold(
        formWithErrors => currentBackLink.map(backLink =>
          BadRequest(template(formWithErrors, service, backLink))
        ),
        formData => {
          businessRegistrationCache.cacheDetails[PaySAQuestion](PaySaDetailsId, formData)
          val paysSa = formData.paySA.getOrElse(false)
          if (paysSa) {
            redirectWithBackLink(businessVerificationController.get.controllerId,
              controllers.routes.BusinessNameController.onPageLoad(service, businessType = "NRL"),
              Some(controllers.nonUKReg.routes.PaySAQuestionController.view(service).url)
            )
          } else {
            redirectWithBackLink(businessRegController.controllerId,
              controllers.nonUKReg.routes.BusinessRegController.register(service, businessType = "NUK"),
              Some(controllers.nonUKReg.routes.PaySAQuestionController.view(service).url)
            )
          }
        }
      )
    }
  }
}
