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

import java.util.UUID
import config.ApplicationConfig
import models.{Address, BusinessRegistration, OverseasCompany, ReviewDetails}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
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
import views.html.nonUkReg.update_overseas_company_registration

import scala.concurrent.Future

class UpdateOverseasCompanyRegControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.auth.host" -> "authprotected")
    .build()

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val service = "ATED"
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBusinessRegistrationService: BusinessRegistrationService = mock[BusinessRegistrationService]
  val injectedViewInstance: update_overseas_company_registration = inject[views.html.nonUkReg.update_overseas_company_registration]

  implicit val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]

  object TestNonUKController extends UpdateOverseasCompanyRegController(
  mockAuthConnector,
  appConfig,
  injectedViewInstance,
  mockBusinessRegistrationService,
  mcc
  )

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockBusinessRegistrationService)
  }

  val serviceName: String = "ATED"

  "UpdateOverseasCompanyRegController" must {

    "unauthorised users" must {
      "respond with a redirect for /register & be redirected to the unauthorised page" in {
        editWithUnAuthorisedUser() { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/business-customer/unauthorised"))
        }
      }

      "respond with a redirect for /send & be redirected to the unauthorised page" in {
        submitWithUnAuthorisedUser() { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/business-customer/unauthorised"))
        }
      }
    }

    "edit client" must {

      "return business registration view for a Non-UK based client with found data" in {
        val busRegData = BusinessRegistration(businessName = "testName",
          businessAddress = Address("line1", "line2", Some("line3"), Some("line4"), Some("postCode"), "country")
        )
        val overseasCompany = OverseasCompany(
          businessUniqueId = Some(s"BUID-${UUID.randomUUID}"),
          hasBusinessUniqueId = Some(true),
          issuingInstitution = Some("issuingInstitution"),
          issuingCountry = None
        )
        when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(("NUK", busRegData, overseasCompany))))

        editClientWithAuthorisedUser(serviceName) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("Do you have an overseas company registration number? - Register as an alcohol wholesaler or producer - GOV.UK")
        }
      }

      "return business registration view for a Non-UK based agent creating a client with found data" in {
        val busRegData = BusinessRegistration(businessName = "testName",
          businessAddress = Address("line1", "line2", Some("line3"), Some("line4"), Some("postCode"), "country")
        )
        val overseasCompany = OverseasCompany(
          businessUniqueId = Some(s"BUID-${UUID.randomUUID}"),
          hasBusinessUniqueId = Some(true),
          issuingInstitution = Some("issuingInstitution"),
          issuingCountry = None
        )

        when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(("NUK", busRegData, overseasCompany))))

        editClientWithAuthorisedAgent(serviceName, Some("/api/anywhere")) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("Do you have an overseas company registration number? - Register as an alcohol wholesaler or producer - GOV.UK")

        }
      }

      "redirect url is invalid format" in {
        editClientWithAuthorisedAgent(serviceName, Some("http://website.com")) { result =>
          status(result) must be(BAD_REQUEST)
        }
      }

      "throw an exception if we have no data" in {

        when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

        editClientWithAuthorisedUser(serviceName) { result =>
          val thrown = the[RuntimeException] thrownBy await(result)
          thrown.getMessage must be("No registration details found")
        }
      }
    }

    "update" must {

      "validate form" must {

        type TestMessage = String
        type ErrorMessage = String

        "not be empty" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "", "issuingInstitution" -> "", "issuingCountry" -> "").toSeq: _*), "ATED") { result =>
            status(result) must be(BAD_REQUEST)
            contentAsString(result) must include("Enter the country that issued the overseas company registration number")
            contentAsString(result) must include("Enter an institution that issued the overseas company registration number")
            contentAsString(result) must include("Enter an overseas company registration number")
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
            registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(data._1.toSeq: _*), "ATED") { result =>
              status(result) must be(BAD_REQUEST)
              contentAsString(result) must include(data._3)
            }
          }
        }

        "If we have no cache then an exception must be thrown" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*),"ATED", None, hasCache = false) { result =>
            val thrown = the[RuntimeException] thrownBy await(result)
            thrown.getMessage must be("No registration details found")
          }
        }

        "If registration details entered are valid, continue button must redirect to the next page" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED") { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result) must be(Some("/business-customer/review-details/ATED"))
          }
        }

        "If registration details entered are valid, continue button must redirect to redirectUrl when present" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED", Some("/api/anywhere")) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result) must be(Some("/api/anywhere"))
          }
        }

        "redirect url is invalid format" in {
          registerWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("hasBusinessUniqueId" -> "true", "businessUniqueId" -> "some-id", "issuingInstitution" -> "some-institution", "issuingCountry" -> "FR").toSeq: _*), "ATED", Some("http://website.com")) { result =>
            status(result) must be(BAD_REQUEST)
          }
        }
      }
    }
  }

  def editWithUnAuthorisedUser()(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    val result = TestNonUKController.viewForUpdate(serviceName, addClient = false, None).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def editClientWithAuthorisedAgent(service: String, redirectUrl: Option[String] = None)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)

    val result = TestNonUKController.viewForUpdate(serviceName, addClient = false, redirectUrl.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def editClientWithAuthorisedUser(service: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = ReviewDetails("ACME", Some("Unincorporated body"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

    when(mockBusinessRegistrationService.updateRegisterBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(successModel))

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val result = TestNonUKController.viewForUpdate(serviceName, addClient = false, None).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }



  def submitWithUnAuthorisedUser(businessType: String = "NUK", redirectUrl: Option[String] = None)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)

    val result = TestNonUKController.update(service, addClient = true, redirectUrl.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def registerWithAuthorisedUserSuccess(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], service: String = service, redirectUrl: Option[String] = None, hasCache: Boolean = true)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val busRegData = BusinessRegistration(businessName = "testName", businessAddress = address)
    val overseasCompany = OverseasCompany(
      businessUniqueId = Some(s"BUID-${UUID.randomUUID}"),
      hasBusinessUniqueId = Some(true),
      issuingInstitution = Some("issuingInstitution"),
      issuingCountry = None
    )
    if (hasCache)
      when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(("NUK", busRegData, overseasCompany))))
    else
      when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val successModel = ReviewDetails("ACME", Some("Unincorporated body"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

    when(mockBusinessRegistrationService.updateRegisterBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(successModel))

    val result = TestNonUKController.update(service, addClient = true, redirectUrl.map(RedirectUrl(_))).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def submitWithAuthorisedUserFailure(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], redirectUrl: Option[String] = None)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val result = TestNonUKController.update(service, addClient = true, redirectUrl.map(RedirectUrl(_))).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

}
