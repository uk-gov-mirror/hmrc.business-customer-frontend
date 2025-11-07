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
import models.{Address, BusinessRegistration}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import views.html.nonUkReg.nonuk_business_registration

import java.util.UUID
import scala.concurrent.Future


class AgentRegisterNonUKClientControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with Injecting {

  val service = "ATED"
  val invalidService = "scooby-doo"
  val injectedViewInstance: nonuk_business_registration = inject[views.html.nonUkReg.nonuk_business_registration]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val mockAuthConnector: DefaultAuthConnector = mock[DefaultAuthConnector]
  private val mockBusinessRegistrationCache: BusinessRegCacheConnector = mock[BusinessRegCacheConnector]
  private val mockBackLinkCache: BackLinkCacheConnector = mock[BackLinkCacheConnector]
  private val mockOverseasController: OverseasCompanyRegController = mock[OverseasCompanyRegController]
  private val mockMCC: MessagesControllerComponents = inject[MessagesControllerComponents]
  private val mockAppConfig: ApplicationConfig = inject[ApplicationConfig]

  class Setup {
    val controller = new AgentRegisterNonUKClientController(
      mockAuthConnector,
      mockBackLinkCache,
      mockAppConfig,
      injectedViewInstance,
      mockBusinessRegistrationCache,
      mockOverseasController,
      mockMCC
    )
  }

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockBusinessRegistrationCache)
    reset(mockBackLinkCache)
    reset(mockOverseasController)
  }

  "AgentRegisterNonUKClientController" must {
    "unauthorised users" must {
      "respond with a redirect for /register & be redirected to the unauthorised page" in new Setup {
        registerWithUnAuthorisedUser(controller = controller) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/business-customer/unauthorised"))
        }
      }

      "respond with a redirect for /send & be redirected to the unauthorised page" in new Setup {
        submitWithUnAuthorisedUser(controller = controller) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/business-customer/unauthorised"))
        }
      }
    }

    "Authorised Users" must {

      "return business registration view for a Non-UK based client by agent with no back link" in new Setup {
        viewWithAuthorisedUser(service, "NUK", None, Some("http://localhost:9959/mandate/agent/inform-HMRC/nrl"), controller) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is your client’s overseas registered business name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: Add a client")
          document.getElementsByTag("h1").text() must include("What is your client’s overseas registered business name and address?")
          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Continue")

          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be("http://localhost:9959/mandate/agent/inform-HMRC/nrl")
        }
      }

      "return business registration view for a Non-UK based client by agent with cached back link" in new Setup {

        val expectedBacklink = "http://localhost:9959/calling-service/backlink"
        viewWithAuthorisedUser(service, "NUK", None, Some(expectedBacklink), controller) { result =>

          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is your client’s overseas registered business name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: Add a client")
          document.getElementsByTag("h1").text() must include("What is your client’s overseas registered business name and address?")
          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Continue")

          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(expectedBacklink)
        }
      }

      "return business registration view for a Non-UK based client by agent and save backlink for future retrieval" in new Setup {

        val expectedBacklink = "http://localhost:9959/calling-service/backlink"

        viewWithAuthorisedUser(service, "NUK", Some(expectedBacklink), None, controller) { result =>

          val document = Jsoup.parse(contentAsString(result))

          verify(mockBackLinkCache, times(1)).
            saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())

          document.title() must be("What is your client’s overseas registered business name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: Add a client")
          document.getElementsByTag("h1").text() must include("What is your client’s overseas registered business name and address?")
          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Continue")

          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(expectedBacklink)
        }
      }

      "return business registration view for a Non-UK based client by agent with a back link" in new Setup {
        when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some("/api/anywhere")))
        viewWithAuthorisedUser(service, "NUK", Some("/api/anywhere"), Some("http://localhost:9959/mandate/agent/inform-HMRC/nrl"), controller) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is your client’s overseas registered business name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: Add a client")
          document.getElementsByTag("h1").text() must include("What is your client’s overseas registered business name and address?")
          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Continue")

          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be("/api/anywhere")
        }
      }


      "return business registration view for a Non-UK based client by agent with some saved data" in new Setup {
        val regAddress: Address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
        val businessReg: BusinessRegistration = BusinessRegistration("ACME", regAddress)
        when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some("/api/anywhere")))
        viewWithAuthorisedUserWithSomeData(service, Some(businessReg), "NUK", Some("/api/anywhere"), Some("http://localhost:9959/mandate/agent/inform-HMRC/nrl"), controller) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is your client’s overseas registered business name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: Add a client")
          document.getElementsByTag("h1").text() must include("What is your client’s overseas registered business name and address?")
          document.getElementById("businessName").`val`() must be("ACME")
          document.getElementById("businessAddress.line_1").`val`() must be("line 1")
          document.getElementById("businessAddress.line_2").`val`() must be("line 2")
          document.getElementById("submit").text() must be("Continue")

          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be("/api/anywhere")
        }
      }

    }

    "send" must {

      "validate form" must {

        def createJson(businessName: String = "ACME",
                       line1: String = "line-1",
                       line2: String = "line-2",
                       line3: String = "",
                       line4: String = "",
                       country: String = "FR")= {
          Map(
            "businessName" -> s"$businessName",
            "businessAddress.line_1" -> s"$line1",
            "businessAddress.line_2" -> s"$line2",
            "businessAddress.line_3" -> s"$line3",
            "businessAddress.line_4" -> s"$line4",
            "businessAddress.country" -> s"$country"
          )
        }

        type TestMessage = String
        type ErrorMessage = String

        "not be empty" in new Setup {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "",
            "businessAddress.line_1" -> "",
            "businessAddress.line_2" -> "",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.country" -> ""
          ).toSeq: _*), controller = controller) { result =>
            val document = Jsoup.parse(contentAsString(result))
            status(result) must be(BAD_REQUEST)
            document.getElementsByClass("govuk-error-summary__body").text() mustBe "Enter a business name Enter address line 1 Enter address line 2 Enter a country"
            document.getElementsByClass("govuk-error-summary__body").text() mustNot include ("Enter a valid postcode")
            document.getElementById("businessName-error").text() mustBe "Error: Enter a business name"
            document.getElementById("businessAddress.line_1-error").text() mustBe "Error: Enter address line 1"
            document.getElementById("businessAddress.line_2-error").text() mustBe "Error: Enter address line 2"
            document.getElementById("businessAddress.country-error").text() mustBe "Error: Enter a country"
          }
        }

        // inputJson , test message, error message
        val formValidationInputDataSet: Seq[(Map[String, String], TestMessage, ErrorMessage)] = Seq(
          (createJson(businessName = "a" * 106), "If entered, Business name must be maximum of 105 characters", "The business name cannot be more than 105 characters"),
          (createJson(line1 = "a" * 36), "If entered, Address line 1 must be maximum of 35 characters", "Address line 1 cannot be more than 35 characters"),
          (createJson(line2 = "a" * 36), "If entered, Address line 2 must be maximum of 35 characters", "Address line 2 cannot be more than 35 characters"),
          (createJson(line3 = "a" * 36), "Address line 3 is optional but if entered, must be maximum of 35 characters", "Address line 3 cannot be more than 35 characters"),
          (createJson(line4 = "a" * 36), "Address line 4 is optional but if entered, must be maximum of 35 characters", "Address line 4 cannot be more than 35 characters"),
          (createJson(country = "GB"), "show an error if country is selected as GB", "You cannot select United Kingdom when entering an overseas address")
        )

        formValidationInputDataSet.foreach { data =>
          s"${data._2}" in new Setup {
            submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(data._1.toSeq: _*), controller = controller) { result =>
              val document = Jsoup.parse(contentAsString(result))
              status(result) must be(BAD_REQUEST)
              document.getElementsByClass("govuk-error-summary__body").text() mustBe data._3
              document.getElementsByClass("govuk-error-message").text() mustBe "Error: " + data._3
            }
          }
        }

        "If registration details entered are valid, continue button must redirect to service specific redirect url" in new Setup {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line_1",
            "businessAddress.line_2" -> "line_2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.country" -> "FR"
          ).toSeq: _*), "ATED",
            Some("http://localhost:9933/ated-subscription/registered-business-address"), controller) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result).get must include("/business-customer/register/non-uk-client/overseas-company/ATED/true?redirectUrl=")
          }
        }

        "respond with NotFound when invalid service is in uri" in new Setup {
          intercept[NotFoundException] {
            submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
              "businessName" -> "ACME",
              "businessAddress.line_1" -> "line_1",
              "businessAddress.line_2" -> "line_2",
              "businessAddress.line_3" -> "",
              "businessAddress.line_4" -> "",
              "businessAddress.country" -> "FR"
            ).toSeq: _*), invalidService, None, controller = controller) { result =>
              status(result) must be(NOT_FOUND)
            }
          }
        }
      }
    }
  }

  def registerWithUnAuthorisedUser(businessType: String = "NUK", backLink: Option[String] = None, controller: AgentRegisterNonUKClientController)
                                  (test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val result = controller.view(service, backLink.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )

    test(result)
  }

  def registerWithAuthorisedAgent(service: String, businessType: String, backLink: Option[String] = None, controller: AgentRegisterNonUKClientController)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = controller.view(service, backLink.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def viewWithAuthorisedUser(service: String, businessType: String, backLink: Option[String] = None, cachedBackLink: Option[String] = None, controller: AgentRegisterNonUKClientController)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(cachedBackLink))
    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[String](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = controller.view(service, backLink.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def viewWithAuthorisedUserWithSomeData(service: String, businessRegistration: Option[BusinessRegistration], businessType: String, backLink: Option[String] = None, cachedBackLink: Option[String] = None, controller: AgentRegisterNonUKClientController)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"
    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = BusinessRegistration("ACME", address)
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(cachedBackLink))
    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[BusinessRegistration](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(successModel)))

    val result = controller.view(service, backLink.map(RedirectUrl(_))).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }


  def submitWithUnAuthorisedUser(businessType: String = "NUK", redirectUrl: Option[String] = Some("http://"), controller: AgentRegisterNonUKClientController)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = controller.submit(service).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def submitWithAuthorisedUserSuccess(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], service: String = service, redirectUrl: Option[String] = Some("http://"), controller: AgentRegisterNonUKClientController)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = BusinessRegistration("ACME", address)

    when(mockBusinessRegistrationCache.cacheDetails[BusinessRegistration](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(successModel))


    val result = controller.submit(service).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def submitWithAuthorisedUserFailure(fakeRequest: FakeRequest[AnyContentAsJson], redirectUrl: Option[String] = Some("http://"), controller: AgentRegisterNonUKClientController)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val result = controller.submit(service).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

}
