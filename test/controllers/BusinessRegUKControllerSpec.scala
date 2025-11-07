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

package controllers

import java.util.UUID

import config.ApplicationConfig
import connectors.{BackLinkCacheConnector, DataCacheConnector}
import models.{Address, BusinessRegistration, ReviewDetails}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import services.BusinessRegistrationService
import uk.gov.hmrc.auth.core.AuthConnector
import views.html.business_group_registration

import scala.concurrent.Future

class BusinessRegUKControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with Injecting {

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val service = "ATED"
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBusinessRegistrationService: BusinessRegistrationService = mock[BusinessRegistrationService]
  val mockBackLinkCache: BackLinkCacheConnector = mock[BackLinkCacheConnector]
  val mockReviewDetailController: ReviewDetailsController = mock[ReviewDetailsController]
  val injectedViewInstance: business_group_registration = inject[views.html.business_group_registration]
  val mockDataCacheConnector: DataCacheConnector = mock[DataCacheConnector]
  val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]

  object TestBusinessRegController extends BusinessRegUKController(
    mockAuthConnector,
    mockBackLinkCache,
    appConfig,
    injectedViewInstance,
    mockBusinessRegistrationService,
    mockReviewDetailController,
    mcc,
    mockDataCacheConnector
  ) {
    override val authConnector: AuthConnector = mockAuthConnector
    override val controllerId = "test"
    override val backLinkCacheConnector: BackLinkCacheConnector = mockBackLinkCache
  }

  val serviceName: String = "ATED"

  "BusinessGBController" must {

    "unauthorised users" must {
      "respond with a redirect for /register & be redirected to the unauthorised page" in {
        registerWithUnAuthorisedUser() { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include("/business-customer/unauthorised")
        }
      }

      "respond with a redirect for /send & be redirected to the unauthorised page" in {
        submitWithUnAuthorisedUser() { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include("/business-customer/unauthorised")
        }
      }
    }

    "Authorised Users" must {

      "return business registration view for a user for Group" in {
        when(mockDataCacheConnector.fetchAndGetBusinessRegistrationDetailsForSession(ArgumentMatchers.any(), ArgumentMatchers.any())) thenReturn
          Future.successful(Some(BusinessRegistration("", Address("", "", Some(""), Some(""), Some(""), ""))))

        registerWithAuthorisedUser("awrs", "GROUP") {
          result =>
            status(result) must be(OK)
            val document = Jsoup.parse(contentAsString(result))

            document.title() must be("Create AWRS group - Register as an alcohol wholesaler or producer - GOV.UK")
            document.getElementsByClass("govuk-caption-xl").text() must be("This section is: AWRS registration")
            document.select("h1").text() must include("Create AWRS group")

            document.getElementsByAttributeValue("for", "businessName").text() must be("Group representative name")
            document.getElementById("businessName-hint").text() must be("This is your registered company name")
            document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
            document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
            document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (Optional)")
            document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (Optional)")
            document.getElementById("submit").text() must be("Continue")
            document.getElementsByAttributeValue("for", "businessAddress.postcode").text() must be("Postcode")
            document.getElementById("businessAddress.country").attr("value") must be("GB")
        }
      }

      "return business registration view for a user for New Business" in {

        when(mockDataCacheConnector.fetchAndGetBusinessRegistrationDetailsForSession(ArgumentMatchers.any(), ArgumentMatchers.any())) thenReturn
          Future.successful(None)
        registerWithAuthorisedUser("awrs", "NEW") {
          result =>
            status(result) must be(OK)
            val document = Jsoup.parse(contentAsString(result))

            document.title() must be("Create AWRS group - Register as an alcohol wholesaler or producer - GOV.UK")
            document.getElementsByClass("govuk-caption-xl").text() must be("This section is: AWRS registration")
            document.select("h1").text() must include("New business details")
            document.getElementsByAttributeValue("for", "businessName").text() must be("Group representative name")
            document.getElementById("businessName-hint").text() must be("This is your registered company name")
            document.getElementsByAttributeValue("for", "businessAddress.line_1").text() must be("Address line 1")
            document.getElementsByAttributeValue("for", "businessAddress.line_2").text() must be("Address line 2")
            document.getElementsByAttributeValue("for", "businessAddress.line_3").text() must be("Address line 3 (Optional)")
            document.getElementsByAttributeValue("for", "businessAddress.line_4").text() must be("Address line 4 (Optional)")
            document.getElementById("submit").text() must be("Continue")
        }
      }
    }

    "send" must {

      "validate form" must {

        type TestMessage = String
        type ErrorMessage = String

        "not be empty for a Group" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("businessName" -> "", "businessAddress.line_1" -> "", "businessAddress.line_2" -> "", "businessAddress.line_3" -> "", "businessAddress.line_4" -> "", "businessAddress.postcode" -> "", "businessAddress.country" -> "GB").toSeq: _*)) {
            result =>
              status(result) must be(BAD_REQUEST)
              contentAsString(result) must include("Enter a business name")
              contentAsString(result) must include("Enter address line 1")
              contentAsString(result) must include("Enter address line 2")
              contentAsString(result) must include("Enter a valid postcode")
          }
        }

        "not contains special character(,) for a Group" in {
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map("businessName" -> "some name", "businessAddress.line_1" -> "line 1", "businessAddress.line_2" -> "line 2", "businessAddress.line_3" -> "", "businessAddress.line_4" -> "", "businessAddress.postcode" -> "AA, AA1", "businessAddress.country" -> "GB").toSeq: _*)) {
            result =>
              status(result) must be(BAD_REQUEST)
              contentAsString(result) must include("Enter a valid postcode")
          }
        }

        // inputJson , test message, error message
        val formValidationInputDataSet: Seq[(Map[String, String], TestMessage, ErrorMessage)] = Seq(
          (Map("businessName" -> s"${"a" * 106}", "businessAddress.line_1" -> "line-1", "businessAddress.line_2" -> "line-2", "businessAddress.line_3" -> "", "businessAddress.line_4" -> "", "businessAddress.postcode" -> "AA1 1AA", "businessAddress.country" -> "GB"), "If entered, Business name must be maximum of 105 characters", "The business name cannot be more than 105 characters"),
          (Map("businessName" -> "ACME", "businessAddress.line_1" -> s"${"a" * 36}", "businessAddress.line_2" -> "line-2", "businessAddress.line_3" -> "", "businessAddress.line_4" -> "", "businessAddress.postcode" -> "AA1 1AA", "businessAddress.country" -> "GB"), "If entered, Address line 1 must be maximum of 35 characters", "Address line 1 cannot be more than 35 characters"),
          (Map("businessName" -> "ACME", "businessAddress.line_1" -> "line-1", "businessAddress.line_2" -> s"${"a" * 36}", "businessAddress.line_3" -> "", "businessAddress.line_4" -> "", "businessAddress.postcode" -> "AA1 1AA", "businessAddress.country" -> "GB"), "If entered, Address line 2 must be maximum of 35 characters", "Address line 2 cannot be more than 35 characters"),
          (Map("businessName" -> "ACME", "businessAddress.line_1" -> "line-1", "businessAddress.line_2" -> "line-2", "businessAddress.line_3" -> s"${"a" * 36}", "businessAddress.line_4" -> "", "businessAddress.postcode" -> "AA1 1AA", "businessAddress.country" -> "GB"), "Address line 3 is optional but if entered, must be maximum of 35 characters", "Address line 3 cannot be more than 35 characters"),
          (Map("businessName" -> "ACME", "businessAddress.line_1" -> "line-1", "businessAddress.line_2" -> "line-2", "businessAddress.line_3" -> "", "businessAddress.line_4" -> s"${"a" * 36}", "businessAddress.postcode" -> "AA1 1AA", "businessAddress.country" -> "GB"), "Address line 4 is optional but if entered, must be maximum of 35 characters", "Address line 4 cannot be more than 35 characters"),
          (Map("businessName" -> "ACME", "businessAddress.line_1" -> "line-1", "businessAddress.line_2" -> "line-2", "businessAddress.line_3" -> "", "businessAddress.line_4" -> "", "businessAddress.postcode" -> s"${"a" * 11}", "businessAddress.country" -> "GB"), "If entered, Postcode must be maximum of 10 characters", "The postcode cannot be more than 10 characters"),
          (Map("businessName" -> "ACME", "businessAddress.line_1" -> "line-1", "businessAddress.line_2" -> "line-2", "businessAddress.line_3" -> "", "businessAddress.line_4" -> "", "businessAddress.postcode" -> "1234567890", "businessAddress.country" -> "GB"), "If entered, Postcode must be a valid postcode", "Enter a valid postcode")
        )

        formValidationInputDataSet.foreach { data =>
          s"${data._2}" in {
            submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(data._1.toSeq: _*)) { result =>
              status(result) must be(BAD_REQUEST)
              contentAsString(result) must include(data._3)
            }
          }
        }

        "If registration details entered are valid, continue button must redirect to review details page" in {
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
          submitWithAuthorisedUserSuccess(FakeRequest("POST", "/").withFormUrlEncodedBody(Map(
            "businessName" -> "ACME",
              "businessAddress.line_1" -> "line-1",
              "businessAddress.line_2" -> "line-2",
              "businessAddress.line_3" -> "",
              "businessAddress.line_4" -> "",
              "businessAddress.postcode" -> "AA1 1AA",
              "businessAddress.country" -> "GB").toSeq: _*)) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result).get must include(s"/business-customer/review-details/$service")
          }
        }
      }
    }
  }

  def registerWithUnAuthorisedUser(businessType: String = "GROUP")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestBusinessRegController.register(serviceName, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )
    test(result)
  }

  def registerWithAuthorisedUser(service: String, businessType: String = "GROUP")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestBusinessRegController.register(service, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )
    test(result)
  }

  def submitWithUnAuthorisedUser(businessType: String = "GROUP")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestBusinessRegController.send(service, businessType).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )
    test(result)
  }

  def submitWithAuthorisedUserSuccess(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], businessType: String = "GROUP")(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")
    val successModel = ReviewDetails("ACME", Some("Unincorporated body"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

    when(mockBusinessRegistrationService.registerBusiness(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(successModel))

    val result = TestBusinessRegController.send(service, businessType).apply(fakeRequest.withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
    )
    test(result)
  }
}
