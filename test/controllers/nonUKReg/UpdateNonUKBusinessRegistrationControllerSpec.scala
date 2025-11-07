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
import views.html.nonUkReg.update_business_registration

import scala.concurrent.Future

class UpdateNonUKBusinessRegistrationControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.auth.host" -> "authprotected")
    .build()

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val service = "ATED"
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBusinessRegistrationService: BusinessRegistrationService = mock[BusinessRegistrationService]
  val injectedViewInstance: update_business_registration = inject[views.html.nonUkReg.update_business_registration]

  implicit val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]

  object TestNonUKController extends UpdateNonUKBusinessRegistrationController(
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

  "UpdateNonUKBusinessRegistrationController" must {

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

        editClientWithAuthorisedUser(serviceName, "NUK") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.getElementsByTag("h1").text() must include("What is your overseas business registered name and address?")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.getElementById("business-reg-lede").text() must be("This is the registered address of your overseas business.")

          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementById("submit").text() must be("Continue")
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

        editClientWithAuthorisedAgent(serviceName, "NUK") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is your client’s overseas registered business name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: Add a client")
          document.getElementsByTag("h1").text() must include("What is your client’s overseas registered business name and address?")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: Add a client")
          document.getElementById("business-reg-lede").text() must be("This is the registered address of your client’s overseas business.")

          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Continue")
        }
      }

      "redirect url is invalid format" in {
        editClientWithAuthorisedAgent(serviceName, "NUK", Some("http://website.com")) { result =>
          status(result) must be(BAD_REQUEST)
        }
      }

      "throw an exception if we have no data" in {
        when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

        editClientWithAuthorisedUser(serviceName, "NUK") { result =>
          val thrown = the[RuntimeException] thrownBy await(result)
          thrown.getMessage must be("No registration details found")
        }
      }
    }

    "edit agent" must {

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

        editAgentWithAuthorisedUser(serviceName, "NUK") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.getElementsByTag("h1").text() must include("What is your overseas business registered name and address?")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.getElementById("business-reg-lede").text() must be("This is the registered address of your overseas business.")

          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementById("submit").text() must be("Continue")
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

        editAgentWithAuthorisedAgent(serviceName, "NUK") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is the registered business name and address of your overseas agency? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByTag("h1").text() must include("What is the registered business name and address of your overseas agency?")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED agency set up")

          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Continue")
        }
      }

      "throw an exception if we have no data" in {

        when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        editAgentWithAuthorisedUser(serviceName, "NUK") { result =>
          val thrown = the[RuntimeException] thrownBy await(result)
          thrown.getMessage must be("No registration details found")
        }
      }
    }

    "update" must {

      "validate form" must {

        def createJson(businessName: String = "ACME",
                       line1: String = "line-1",
                       line2: String = "line-2",
                       line3: String = "",
                       line4: String = "",
                       country: String = "FR",
                       postcode: String = "AA1 1AA") = {
          Map(
            "businessName" -> s"$businessName",
            "businessAddress.line_1" -> s"$line1",
            "businessAddress.line_2" -> s"$line2",
            "businessAddress.line_3" -> s"$line3",
            "businessAddress.line_4" -> s"$line4",
            "businessAddress.postcode" -> s"$postcode",
            "businessAddress.country" -> s"$country"
          )
        }

        type TestMessage = String
        type ErrorMessage = String

        "not be empty" in {
          val inputJson = createJson(businessName = "", line1 = "", line2 = "", country = "")

          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(inputJson.toSeq: _*)) { result =>
            status(result) must be(BAD_REQUEST)
            contentAsString(result) must include("Enter a business name")
            contentAsString(result) must include("Enter address line 1")
            contentAsString(result) must include("Enter address line 2")
            contentAsString(result) mustNot include("Enter a valid postcode")
            contentAsString(result) must include("Enter a country")
          }
        }

        // inputJson , test message, error message
        val formValidationInputDataSet: Seq[(Map[String, String], TestMessage, ErrorMessage)] = Seq(
          (createJson(businessName = "a" * 106), "If entered, Business name must be maximum of 105 characters",
            "The business name cannot be more than 105 characters"),
          (createJson(line1 = "a" * 36), "If entered, Address line 1 must be maximum of 35 characters",
            "Address line 1 cannot be more than 35 characters"),
          (createJson(line2 = "a" * 36), "If entered, Address line 2 must be maximum of 35 characters",
            "Address line 2 cannot be more than 35 characters"),
          (createJson(line3 = "a" * 36), "Address line 3 is optional but if entered, must be maximum of 35 characters",
            "Address line 3 cannot be more than 35 characters"),
          (createJson(line4 = "a" * 36), "Address line 4 is optional but if entered, must be maximum of 35 characters",
            "Address line 4 cannot be more than 35 characters"),
          (createJson(country = "GB"), "show an error if country is selected as GB", "You cannot select United Kingdom when entering an overseas address")
        )

        formValidationInputDataSet.foreach { data =>
          s"${data._2}" in {
            submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(data._1.toSeq: _*)) { result =>
              status(result) must be(BAD_REQUEST)
              contentAsString(result) must include(data._3)
            }
          }
        }

        "If registration details entered are valid, continue button must redirect to service specific redirect url" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "AA1 1AA",
            "businessAddress.country" -> "FR").toSeq: _*), "ATED", Some("/ated-subscription/registered-business-address")) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result) must be(Some("/ated-subscription/registered-business-address"))
          }
        }

        "redirect url is invalid format" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "AA1 1AA",
            "businessAddress.country" -> "FR").toSeq: _*), "ATED", Some("http://website.com")) { result =>
            status(result) must be(BAD_REQUEST)
          }
        }

        "If we have no cache then an exception must be thrown" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "AA1 1AA",
            "businessAddress.country" -> "FR").toSeq: _*), "ATED", None, hasCache = false) { result =>
            val thrown = the[RuntimeException] thrownBy await(result)
            thrown.getMessage must be("No registration details found")
          }
        }

        "redirect to the review details page if we have no redirect url" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "AA1 1AA",
            "businessAddress.country" -> "FR").toSeq: _*), "ATED", None) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result) must be(Some("/business-customer/review-details/ATED"))
          }
        }

        "fail if we are a client for ATED and have no PostCode" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR").toSeq: _*), "ATED", None) { result =>
            status(result) must be(BAD_REQUEST)
          }
        }

        "pass if we are a client for AWRS and have no PostCode" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR").toSeq: _*), "AWRS", None) { result =>
            status(result) must be(SEE_OTHER)
          }
        }

        "pass if we are an agent for ATED and have no PostCode" in {
          submitWithAuthorisedAgent(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR").toSeq: _*), "ATED", None) { result =>
            status(result) must be(SEE_OTHER)
          }
        }

        "pass if we are an agent for AWRS and have no PostCode" in {
          submitWithAuthorisedAgent(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line-1",
            "businessAddress.line_2" -> "line-2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR").toSeq: _*), "ATED", None) { result =>
            status(result) must be(SEE_OTHER)
          }
        }
      }
    }
  }

  def editWithUnAuthorisedUser(businessType: String = "NUK")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    val result = TestNonUKController.edit(serviceName, None).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )


    test(result)
  }

  def editClientWithAuthorisedAgent(service: String, businessType: String, redirectUrl: Option[String] = None)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)

    val result = TestNonUKController.edit(serviceName, redirectUrl.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def editClientWithAuthorisedUser(service: String, businessType: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"


    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = ReviewDetails("ACME", Some("Unincorporated body"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

    when(mockBusinessRegistrationService.updateRegisterBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(successModel))


    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val result = TestNonUKController.edit(serviceName, None).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def editAgentWithAuthorisedAgent(service: String, businessType: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)

    val result = TestNonUKController.editAgent(serviceName).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def editAgentWithAuthorisedUser(service: String, businessType: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"


    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = ReviewDetails("ACME", Some("Unincorporated body"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

    when(mockBusinessRegistrationService.updateRegisterBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(successModel))


    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val result = TestNonUKController.editAgent(serviceName).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def submitWithUnAuthorisedUser(businessType: String = "NUK", redirectUrl: Option[String] = Some("/api/anywhere"))(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)

    val result = TestNonUKController.update(service, redirectUrl.map(RedirectUrl(_)), isRegisterClient = true).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def submitWithAuthorisedUserSuccess(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], service: String = service, redirectUrl: Option[String] = Some("/api/anywhere"), hasCache: Boolean = true)(test: Future[Result] => Any): Unit = {
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
    if (hasCache) {
      when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(("NUK", busRegData, overseasCompany))))
    } else {
      when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    }

    val successModel = ReviewDetails("ACME", Some("Unincorporated body"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

    when(mockBusinessRegistrationService.updateRegisterBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(successModel))

    val result = TestNonUKController.update(service, redirectUrl.map(RedirectUrl(_)), isRegisterClient = true).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def submitWithAuthorisedAgent(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], service: String = service, redirectUrl: Option[String] = Some("/api/anywhere"), hasCache: Boolean = true)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)

    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val busRegData = BusinessRegistration(businessName = "testName", businessAddress = address)
    val overseasCompany = OverseasCompany(
      businessUniqueId = Some(s"BUID-${UUID.randomUUID}"),
      hasBusinessUniqueId = Some(true),
      issuingInstitution = Some("issuingInstitution"),
      issuingCountry = None
    )
    if (hasCache) {
      when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(("NUK", busRegData, overseasCompany))))
    } else {
      when(mockBusinessRegistrationService.getDetails()(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    }

    val successModel = ReviewDetails("ACME", Some("Unincorporated body"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

    when(mockBusinessRegistrationService.updateRegisterBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(successModel))

    val result = TestNonUKController.update(service, redirectUrl.map(RedirectUrl(_)), isRegisterClient = true).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def submitWithAuthorisedUserFailure(fakeRequest: FakeRequest[AnyContentAsJson], redirectUrl: Option[String] = Some("/api/anywhere"))(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val result = TestNonUKController.update(service, redirectUrl.map(RedirectUrl(_)), isRegisterClient = true).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }
}
