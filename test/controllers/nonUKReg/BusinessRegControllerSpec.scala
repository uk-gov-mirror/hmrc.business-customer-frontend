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
import connectors.{BackLinkCacheConnector, BusinessRegCacheConnector}
import models.{Address, BusinessRegistration}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.NotFoundException
import views.html.nonUkReg.business_registration

import scala.concurrent.Future

class BusinessRegControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with Injecting {

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val serviceName = "ATED"
  val invalidService = "scooby-doo"
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBusinessRegistrationCache: BusinessRegCacheConnector = mock[BusinessRegCacheConnector]
  val mockBackLinkCache: BackLinkCacheConnector = mock[BackLinkCacheConnector]
  val mockOverseasCompanyRegController: OverseasCompanyRegController = mock[OverseasCompanyRegController]
  val injectedViewInstance: business_registration = inject[views.html.nonUkReg.business_registration]

  val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]

  object TestBusinessRegController extends BusinessRegController(
    mockAuthConnector,
    mockBackLinkCache,
    appConfig,
    injectedViewInstance,
    mockBusinessRegistrationCache,
    mockOverseasCompanyRegController,
    mcc
  ) {
    override val controllerId = "test"
  }

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockBusinessRegistrationCache)
    reset(mockBackLinkCache)
  }

  "BusinessRegController" must {

    "unauthorised users" must {
      "respond with a redirect for /register & be redirected to the unauthorised page" in {
        registerWithUnAuthorisedUser("NUK") { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/business-customer/unauthorised"))
        }
      }

      "respond with a redirect for /send & be redirected to the unauthorised page" in {
        submitWithUnAuthorisedUser(service = serviceName, "NUK") { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/business-customer/unauthorised"))
        }
      }
    }

    "Authorised Users" must {

      "return business registration view for a user for Non-UK" in {

        registerWithAuthorisedUser(serviceName, "NUK") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is your overseas business registered name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.select("h1").text must include("What is your overseas business registered name and address?")
          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Continue")
        }
      }


      "return business registration view for a user for Non-UK with saved data" in {
        val regAddress = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
        val businessReg = BusinessRegistration("ACME", regAddress)
        registerWithAuthorisedUserWithSomeData(serviceName, "NUK", Some(businessReg)) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is your overseas business registered name and address? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.select("h1").text must include("What is your overseas business registered name and address?")
          document.getElementById("businessName").`val`() must be("ACME")
          document.getElementById("businessAddress.line_1").`val`() must be("line 1")
          document.getElementById("businessAddress.line_2").`val`() must be("line 2")
          document.getElementById("submit").text() must be("Continue")
        }
      }


      "return business registration view for an agent" in {

        registerWithAuthorisedAgent(serviceName, "NUK") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))

          document.title() must be("What is the registered business name and address of your overseas agency? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED agency set up")
          document.select("h1").text must include("What is the registered business name and address of your overseas agency?")
          document.getElementsByAttributeValue("for", "businessName").text() must be("Business name")
          document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
          document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
          document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (optional)")
          document.getElementsByAttributeValue("for", "businessAddress.country").text() must include("Country")
          document.getElementById("submit").text() must be("Register your agency")
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
                       postcode: String = "12345678",
                       country: String = "FR") = {
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
          val inputJson = createJson(businessName = "", line1 = "", line2 = "", postcode = "", country = "")
          when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

          submitWithAuthorisedUserSuccess(serviceName, FakeRequest("POST", "/").withFormUrlEncodedBody(inputJson.toSeq: _*)) { result =>
            status(result) must be(BAD_REQUEST)
            contentAsString(result) must include("Enter a business name")
            contentAsString(result) must include("Enter address line 1")
            contentAsString(result) must include("Enter address line 2")
            contentAsString(result) mustNot include("Postcode must be entered")
            contentAsString(result) must include("Enter a country")
          }
        }

        // inputJson , test message, error message
        val formValidationInputDataSet: Seq[(Map[String, String], TestMessage, ErrorMessage)] = Seq(
          (createJson(businessName = "a" * 106), "If entered, Business name must be maximum of 105 characters", "The business name cannot be more than 105 characters"),
          (createJson(line1 = "a" * 36), "If entered, Address line 1 must be maximum of 35 characters", "Address line 1 cannot be more than 35 characters"),
          (createJson(line2 = "a" * 36), "If entered, Address line 2 must be maximum of 35 characters", "Address line 2 cannot be more than 35 characters"),
          (createJson(line3 = "a" * 36), "Address line 3 is optional but if entered, must be maximum of 35 characters", "Address line 3 cannot be more than 35 characters"),
          (createJson(line4 = "a" * 36), "Address line 4 is optional but if entered, must be maximum of 35 characters", "Address line 4 cannot be more than 35 characters"),
          (createJson(postcode = "a" * 11), "Postcode is optional but if entered, must be maximum of 10 characters", "Enter a valid postcode"),
          (createJson(country = "GB"), "show an error if country is selected as GB", "You cannot select United Kingdom when entering an overseas address")
        )

        formValidationInputDataSet.foreach { data =>
          s"${data._2}" in {
            when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
            submitWithAuthorisedUserSuccess(serviceName, FakeRequest("POST", "/").withFormUrlEncodedBody(data._1.toSeq: _*)) { result =>
              status(result) must be(BAD_REQUEST)
              contentAsString(result) must include(data._3)
            }
          }
        }

        "If registration details entered are valid, continue button must redirect to review details page" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          val inputJson = createJson()
          submitWithAuthorisedUserSuccess(serviceName, FakeRequest("POST", "/").withFormUrlEncodedBody(inputJson.toSeq: _*)) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result).get must include(s"/business-customer/register/non-uk-client/overseas-company/$serviceName/false")
          }
        }

        "If registration details entered are valid and business-identifier question is selected as No, continue button must redirect to review details page" in {
          val inputJson = createJson()
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          submitWithAuthorisedUserSuccess(serviceName, FakeRequest("POST", "/").withFormUrlEncodedBody(inputJson.toSeq: _*)) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result).get must include(s"/business-customer/register/non-uk-client/overseas-company/$serviceName/false")
          }
        }


        "fail if we are a client for ATED and have no PostCode" in {
          when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          submitWithAuthorisedUserSuccess(serviceName, FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line_1",
            "businessAddress.line_2" -> "line_2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR"
          ).toSeq: _*)) { result =>
            status(result) must be(BAD_REQUEST)
          }
        }

        "pass if we are a client for AWRS and have no PostCode" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          submitWithAuthorisedUserSuccess("AWRS", FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line_1",
            "businessAddress.line_2" -> "line_2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR"
          ).toSeq: _*)) { result =>
            status(result) must be(SEE_OTHER)
          }
        }

        "pass if we are an agent for ATED and have no PostCode" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          submitWithAuthorisedAgent(serviceName, FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line_1",
            "businessAddress.line_2" -> "line_2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR"
          ).toSeq: _*)) { result =>
            status(result) must be(SEE_OTHER)
          }
        }

        "pass if we are an agent for AWRS and have no PostCode" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          submitWithAuthorisedAgent("AWRS", FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
            "businessAddress.line_1" -> "line_1",
            "businessAddress.line_2" -> "line_2",
            "businessAddress.line_3" -> "",
            "businessAddress.line_4" -> "",
            "businessAddress.postcode" -> "",
            "businessAddress.country" -> "FR"
          ).toSeq: _*)) { result =>
            status(result) must be(SEE_OTHER)
          }
        }

        "respond with NotFound when invalid service is in uri" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          intercept[NotFoundException] {
            submitWithAuthorisedAgent(invalidService, FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
              "businessName" -> "ACME",
              "businessAddress.line_1" -> "line_1",
              "businessAddress.line_2" -> "line_2",
              "businessAddress.line_3" -> "",
              "businessAddress.line_4" -> "",
              "businessAddress.postcode" -> "",
              "businessAddress.country" -> "FR"
            ).toSeq: _*)) { result =>
              status(result) must be(NOT_FOUND)
            }
          }
        }
      }
    }
  }

  def registerWithUnAuthorisedUser(businessType: String = "NUK")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val result = TestBusinessRegController.register(serviceName, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def registerWithAuthorisedAgent(service: String, businessType: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[String](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestBusinessRegController.register(service, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def registerWithAuthorisedUser(service: String, businessType: String)(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[String](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestBusinessRegController.register(service, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def registerWithAuthorisedUserWithSomeData(service: String, businessType: String, businessRegistration: Option[BusinessRegistration])(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"
    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = BusinessRegistration("ACME", address)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[BusinessRegistration](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(successModel)))

    val result = TestBusinessRegController.register(service, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }


  def submitWithUnAuthorisedUser(service: String, businessType: String = "NUK")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)

    val result = TestBusinessRegController.send(service, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def submitWithAuthorisedUserSuccess(service: String, fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], businessType: String = "NUK")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = BusinessRegistration("ACME", address)

    when(mockBusinessRegistrationCache.cacheDetails[BusinessRegistration](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(successModel))

    val result = TestBusinessRegController.send(service, businessType).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def submitWithAuthorisedAgent(service: String, fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], businessType: String = "NUK")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)

    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = BusinessRegistration("ACME", address)
    when(mockBusinessRegistrationCache.cacheDetails[BusinessRegistration](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(successModel))

    val result = TestBusinessRegController.send(service, businessType).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

  def submitWithAuthorisedUserFailure(service: String, fakeRequest: FakeRequest[AnyContentAsJson], businessType: String = "NUK")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

    val result = TestBusinessRegController.send(service, businessType).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }

}
