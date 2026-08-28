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
import controllers.BackLinkController
import controllers.auth.AuthActions
import forms.BusinessRegistrationForms._

import javax.inject.Inject
import models.NRLQuestion
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.BusinessCustomerConstants.NrlFormId

import scala.concurrent.{ExecutionContext, Future}

class NRLQuestionController @Inject()(val authConnector: AuthConnector,
                                      val backLinkCacheConnector: BackLinkCacheConnector,
                                      config: ApplicationConfig,
                                      template: views.html.nonUkReg.nrl_question,
                                      businessRegController: BusinessRegController,
                                      mcc: MessagesControllerComponents,
                                      paySAQuestionController: PaySAQuestionController,
                                      businessRegistrationCache: BusinessRegCacheConnector)
  extends FrontendController(mcc) with AuthActions with BackLinkController {

  implicit val appConfig: ApplicationConfig = config
  implicit val executionContext: ExecutionContext = mcc.executionContext
  val controllerId: String = "NRLQuestionController"

  def view(service: String): Action[AnyContent] = Action.async { implicit request =>
    authorisedFor(service) { implicit authContext =>
      if (authContext.isAgent) {
        forwardBackLinkToNextPage(businessRegController.controllerId, controllers.nonUKReg.routes.BusinessRegController.register(service, businessType = "NUK"))
      } else {
        val backLinkUrl = request.getQueryString("backLinkUrl")
        for {
          _ <- if (service == "ATED" && backLinkUrl.isDefined)
            setBackLink(controllerId, backLinkUrl)
          else
            Future.successful(None)
          backLink <- currentBackLink
          savedNRL <- businessRegistrationCache.fetchAndGetCachedDetails[NRLQuestion](NrlFormId)
        } yield {
          Ok(template(nrlQuestionForm.fill(savedNRL.getOrElse(NRLQuestion())), service, backLink))
        }
      }
    }
  }

  def continue(service: String): Action[AnyContent] = Action.async { implicit request =>
    authorisedFor(service){ implicit authContext =>
      nrlQuestionForm.bindFromRequest().fold(
        formWithErrors =>
          currentBackLink.map(backLink => BadRequest(template(formWithErrors, service, backLink))),
        formData => {
          businessRegistrationCache.cacheDetails[NRLQuestion](NrlFormId, formData)
          val paysSa = formData.paysSA.getOrElse(false)
          if (paysSa) {
            redirectWithBackLink(paySAQuestionController.controllerId,
              controllers.nonUKReg.routes.PaySAQuestionController.view(service),
              Some(controllers.nonUKReg.routes.NRLQuestionController.view(service).url)
            )
          } else {
            redirectWithBackLink(businessRegController.controllerId,
              controllers.nonUKReg.routes.BusinessRegController.register(service, businessType = "NUK"),
              Some(controllers.nonUKReg.routes.NRLQuestionController.view(service).url)
            )
          }
        }
      )
    }
  }

}
