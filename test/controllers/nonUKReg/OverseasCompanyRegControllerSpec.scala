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
import controllers.ReviewDetailsController
import models.{Address, BusinessRegistration, OverseasCompany, ReviewDetails}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import services.BusinessRegistrationService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import views.html.nonUkReg.overseas_company_registration

import java.util.UUID
import scala.concurrent.Future

class OverseasCompanyRegControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.auth.host" -> "authprotected")
    .build()

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val service = "ATED"
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBusinessRegistrationService: BusinessRegistrationService = mock[BusinessRegistrationService]
  val mockBusinessRegistrationCache: BusinessRegCacheConnector = mock[BusinessRegCacheConnector]
  val mockBackLinkCache: BackLinkCacheConnector = mock[BackLinkCacheConnector]
  val injectedViewInstance: overseas_company_registration = inject[views.html.nonUkReg.overseas_company_registration]

  val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]

  val mockReviewDetailsController: ReviewDetailsController = mock[ReviewDetailsController]

  object TestController extends OverseasCompanyRegController(
  mockAuthConnector,
  mockBackLinkCache,
  appConfig,
  injectedViewInstance,
  mockBusinessRegistrationService,
  mockBusinessRegistrationCache,
  mockReviewDetailsController,
  mcc
  ) {
    override val controllerId = "test"
  }

  val serviceName: String = "ATED"

  "OverseasCompanyRegController" must {

    "unauthorised users" must {
      "respond with a redirect for /view & be redirected to the unauthorised page" in {
        viewWithUnAuthorisedUser() { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/business-customer/unauthorised"))
        }
      }
    }

    "view" must {

      "return business registration view for a user for Non-UK" in {

        viewWithAuthorisedUser(serviceName) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("Do you have an overseas company registration number? - Register as an alcohol wholesaler or producer - GOV.UK")
        }
      }

      "redirect url is invalid format" in {
        viewWithAuthorisedUser(serviceName, Some("http://website.com")) { result =>
          status(result) must be(BAD_REQUEST)
        }
      }

      "return business registration view for a user for Non-UK with saved data" in {
        viewWithAuthorisedAgentWithSomeData(serviceName) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("Does your client have an overseas company registration number? - Register as an alcohol wholesaler or producer - GOV.UK")
        }
      }

    }

    "send" must {

      val regAddress = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "GB")
      val businessReg = BusinessRegistration("ACME", regAddress)
      val overseasDetails = OverseasCompany(Some(true), Some("1234"))
      val reviewDetails = ReviewDetails("ACME", Some("Unincorporated body"), regAddress, "sap123", "safe123",
        isAGroup = false, directMatch = false, Some("agent123"))

      "validate form" must {

        type TestMessage = String
        type ErrorMessage = String

        "select a radio button" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "").toSeq: _*), "ATED", Some(businessReg), overseasDetails,reviewDetails) { result =>
            val document = Jsoup.parse(contentAsString(result))
            status(result) must be(BAD_REQUEST)
            document.getElementsByClass("govuk-error-summary__body").text() mustBe "Select yes if you have an overseas company registration number"
            document.getElementById("hasBusinessUniqueId-error").text() mustBe "Error: Select yes if you have an overseas company registration number"
          }
        }

        "not be empty" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "", "issuingInstitution" -> "", "issuingCountry" -> "").toSeq: _*), "ATED", Some(businessReg), overseasDetails,reviewDetails) { result =>
            val document = Jsoup.parse(contentAsString(result))
            status(result) must be(BAD_REQUEST)
            document.getElementsByClass("govuk-error-summary__body").text() mustBe "Enter an overseas company registration number Enter the country that issued the overseas company registration number Enter an institution that issued the overseas company registration number"
            document.getElementById("businessUniqueId-error").text() mustBe "Error: Enter an overseas company registration number"
            document.getElementById("issuingCountry-error").text() mustBe "Error: Enter the country that issued the overseas company registration number"
            document.getElementById("issuingInstitution-error").text() mustBe "Error: Enter an institution that issued the overseas company registration number"
          }
        }

        // inputJson , test message, error message
        val formValidationInputDataSet: Seq[(Map[String, String], TestMessage, ErrorMessage)] = Seq(
          (Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> s"${"a" * 61}", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR"), "businessUniqueId must be maximum of 60 characters",
            "The overseas company registration number cannot be more than 60 characters"),
          (Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> s"${"a" * 41}", "issuingCountry" -> "FR"), "issuingInstitution must be maximum of 40 characters",
            "The institution that issued the overseas company registration number cannot be more than 40 characters"),
          (Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "GB"), "show an error if issuing country is selected as GB",
            "You cannot select United Kingdom when entering an overseas address")
        )

        formValidationInputDataSet.foreach { data =>
          s"${data._2}" in {
            registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(data._1.toSeq: _*), "ATED", Some(businessReg),overseasDetails, reviewDetails) { result =>
              val document = Jsoup.parse(contentAsString(result))
              status(result) must be(BAD_REQUEST)
              document.getElementsByClass("govuk-error-summary__body").text() mustBe data._3
              document.getElementsByClass("govuk-error-message").text() mustBe "Error: " + data._3
            }
          }
        }

        "If we have no cache then an exception must be thrown" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED", None,overseasDetails, reviewDetails) { result =>
            val thrown = the[RuntimeException] thrownBy await(result)
            thrown.getMessage must be("[OverseasCompanyRegController][send] - service :ATED. Error : No Cached BusinessRegistration")
          }
        }

        "If registration details entered are valid, continue button must redirect to the redirectUrl" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED", Some(businessReg),overseasDetails, reviewDetails) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result) must be(Some("/business-customer/review-details/ATED"))
          }
        }

        "If registration details entered are valid, continue button must redirect with to next page if no redirectUrl" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED",
            Some(businessReg),overseasDetails, reviewDetails, Some("/api/anywhere")) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result) must be(Some("/api/anywhere"))
          }
        }

        "If updateNoRegister flag is set to true, must update registration rather then create a new one" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          updateWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED", Some(businessReg),overseasDetails, reviewDetails) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result) must be(Some("/business-customer/review-details/ATED"))
          }
          verify(mockBusinessRegistrationService, times(1))
            .updateRegisterBusiness(
              ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()
            )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        }

        "redirect url is invalid format" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED",
            Some(businessReg),overseasDetails, reviewDetails, Some("http://website.com")) { result =>
            status(result) must be(BAD_REQUEST)
          }
        }
      }
    }
  }

  def viewWithUnAuthorisedUser()(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestController.view(serviceName, addClient = true).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def viewWithAuthorisedAgent(service: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[String](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestController.view(service, addClient = true).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def viewWithAuthorisedAgentWithSomeData(service: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[OverseasCompany](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(OverseasCompany(Some(true)))))

    val result = TestController.view(service, addClient = true).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }


  def viewWithAuthorisedUser(service: String, redirectUrl: Option[String] = None)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[String](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestController.view(service, addClient = true, redirectUrl.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def registerWithAuthorisedUserSuccess(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
                                    service: String = service,
                                    busRegCache : Option[BusinessRegistration] = None,
                                    overseasSave : OverseasCompany,
                                    reviewDetails : ReviewDetails,
                                    redirectUrl: Option[String] = None)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[BusinessRegistration](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(busRegCache))

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[Boolean](ArgumentMatchers.eq("Update_No_Register"))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(None))

    when(mockBusinessRegistrationCache.cacheDetails[OverseasCompany](ArgumentMatchers.any(), ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(overseasSave))

    when(mockBusinessRegistrationService.registerBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(reviewDetails))

    val result = TestController.register(service, addClient = true, redirectUrl.map(RedirectUrl(_))).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def updateWithAuthorisedUserSuccess(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
                                      service: String = service,
                                      busRegCache : Option[BusinessRegistration] = None,
                                      overseasSave : OverseasCompany,
                                      reviewDetails : ReviewDetails,
                                      redirectUrl: Option[String] = None)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[BusinessRegistration](ArgumentMatchers.eq("BC_NonUK_Business_Details"))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(busRegCache))

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[Boolean](ArgumentMatchers.eq("Update_No_Register"))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(Some(true)))

    when(mockBusinessRegistrationCache.cacheDetails[OverseasCompany](ArgumentMatchers.any(), ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(overseasSave))

    when(mockBusinessRegistrationService.updateRegisterBusiness(
      ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()
    )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(reviewDetails))

    val result = TestController.register(service, addClient = true, redirectUrl.map(RedirectUrl(_))).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

}
