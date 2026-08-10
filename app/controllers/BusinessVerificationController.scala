/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers

import config.ApplicationConfig
import connectors.{BackLinkCacheConnector, BusinessRegCacheConnector}
import controllers.auth.AuthActions
import controllers.nonUKReg.{BusinessRegController, NRLQuestionController}
import forms.BusinessVerificationForms._
import forms._
import play.api.i18n.I18nSupport
import play.api.libs.json.Format.GenericFormat
import play.api.mvc._
import services.BusinessMatchingService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.BusinessCustomerConstants._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessVerificationController @Inject()(val config: ApplicationConfig,
                                               val authConnector: AuthConnector,
                                               template: views.html.business_verification,
                                               templateDetailsNotFound: views.html.details_not_found,
                                               businessRegCacheConnector: BusinessRegCacheConnector,
                                               val backLinkCacheConnector: BackLinkCacheConnector,
                                               val businessMatchingService: BusinessMatchingService,
                                               val businessRegUKController: BusinessRegUKController,
                                               val businessRegController: BusinessRegController,
                                               val nrlQuestionController: NRLQuestionController,
                                               val reviewDetailsController: ReviewDetailsController,
                                               val homeController: HomeController,
                                               val mcc: MessagesControllerComponents)
  extends FrontendController(mcc) with AuthActions with BackLinkController with I18nSupport {

  implicit val appConfig: ApplicationConfig = config
  implicit val executionContext: ExecutionContext = mcc.executionContext
  val controllerId: String = "BusinessVerificationController"
  val services: Seq[String] = Seq("awrs", "ated", "amls")

  def businessVerification(service: String): Action[AnyContent] = Action.async { implicit request =>
    authorisedFor(service) { implicit authContext =>
      for {
        rawBackLink <- currentBackLink
        backLink: Option[String] = rawBackLink.orElse {
          service match {
            case "AWRS" => Some(appConfig.haveYouRegisteredUrl)
            case _      => rawBackLink
          }
        }
        businessTypeDetails <- businessRegCacheConnector.fetchAndGetCachedDetails[BusinessType](s"$CacheRegistrationDetails$service")
      } yield {
        businessTypeDetails match {
          case Some(businessTypeData) =>
            Ok(template(businessTypeForm.fill(businessTypeData), authContext.isAgent, service, authContext.isSa, authContext.isOrg, backLink))
          case None =>
            Ok(template(businessTypeForm, authContext.isAgent, service, authContext.isSa, authContext.isOrg, backLink))
        }
      }
    }
  }

  def continue(service: String): Action[AnyContent] = Action.async { implicit request =>
    authorisedFor(service) { implicit authContext =>
      BusinessVerificationForms
        .validateBusinessType(businessTypeForm.bindFromRequest(), service)
        .fold(
          formWithErrors => currentBackLink map (backLink => BadRequest(template(formWithErrors, authContext.isAgent, service, authContext.isSa, authContext.isOrg, backLink))),
          value => {
            if (services.exists(_.equalsIgnoreCase(service))) businessRegCacheConnector.cacheDetails(s"$CacheRegistrationDetails$service", value)
            val returnCall = Some(routes.BusinessVerificationController.businessVerification(service).url)
            value.businessType match {
              case Some("NUK") if service.equals("capital-gains-tax") =>
                redirectWithBackLink(businessRegController.controllerId, controllers.nonUKReg.routes.BusinessRegController.register(service, "NUK"), returnCall)
              case Some("NUK") if service.equals("ATED") && !authContext.isAgent =>
                redirectToExternal(
                  appConfig.conf.getConfString(s"ated.overseasSameAccountUrl", throw new Exception("")),
                  Some(controllers.routes.BusinessVerificationController.businessVerification(service).url)
                )
              case Some("NUK") =>
                redirectWithBackLink(nrlQuestionController.controllerId, controllers.nonUKReg.routes.NRLQuestionController.view(service), returnCall)
              case Some("NEW") =>
                redirectWithBackLink(businessRegUKController.controllerId, controllers.routes.BusinessRegUKController.register(service, "NEW"), returnCall)
              case Some("GROUP") =>
                redirectWithBackLink(businessRegUKController.controllerId, controllers.routes.BusinessRegUKController.register(service, "GROUP"), returnCall)
              case Some(busType @ ("SOP" | "UIB" | "LTD" | "OBP" | "LLP" | "LP" | "UT" | "ULTD" | "NRL")) =>
                Future.successful(Redirect(controllers.routes.BusinessNameController.onPageLoad(service, busType)))
              case _ =>
                redirectWithBackLink(homeController.controllerId, controllers.routes.HomeController.homePage(service), returnCall)
            }
          }
        )
    }
  }

  def detailsNotFound(service: String, businessType: String): Action[AnyContent] = Action.async { implicit request =>
    authorisedFor(service) { implicit authContext =>
      val backLink = Some(routes.BusinessUtrController.onPageLoad(service, businessType).url)
      Future.successful(
        Ok(
          templateDetailsNotFound(authContext.isAgent, service, businessType, backLink)
        ))
    }
  }
}
