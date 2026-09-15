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

import config.ApplicationConfig
import connectors.{BackLinkCacheConnector, DataCacheConnector}
import models.{Address, ReviewDetails}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Headers, MessagesControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import play.api.i18n.{Lang, Messages, MessagesApi}
import services.AgentRegistrationService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import views.html.{global_error, review_details, review_details_non_uk_agent}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}


class ReviewDetailsControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with Injecting {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val messages: Messages = inject[MessagesApi].preferred(Seq(Lang.defaultLang))

  val service = "ATED"

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockAgentRegistrationService: AgentRegistrationService = mock[AgentRegistrationService]
  val mockBackLinkCache: BackLinkCacheConnector = mock[BackLinkCacheConnector]
  val mockHttpClient: HttpClientV2 = inject[HttpClientV2]
  val injectedViewInstanceNonUkAgent: review_details_non_uk_agent = inject[views.html.review_details_non_uk_agent]
  val injectedViewInstanceReviewDetails: review_details = inject[views.html.review_details]
  val injectedViewInstanceError: global_error = inject[views.html.global_error]

  implicit val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]

  val address: Address = Address("line 1", "line 2", Some("line 3"), Some("line 4"), Some("AA1 1AA"), "UK")

  val directMatchReviewDetails: ReviewDetails = ReviewDetails("ACME", Some("Limited"), address, "sap123", "safe123", isAGroup = false, directMatch = true, Some("agent123"))

  val nonDirectMatchReviewDetails: ReviewDetails = ReviewDetails("ACME", Some("Limited"), address, "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))

  val badGatewayResponse: JsValue = Json.parse( """{"statusCode":502,"message":"<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/03/addressing\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"><soap:Header><wsa:Action>http://schemas.xmlsoap.org/ws/2004/03/addressing/fault</wsa:Action><wsa:MessageID>uuid:199814d0-9758-49d1-a2c0-d24300f67e2c</wsa:MessageID><wsa:RelatesTo>uuid:d1894fa0-b97d-4707-a814-e0c5ea79a01a</wsa:RelatesTo><wsa:To>http://schemas.xmlsoap.org/ws/2004/03/addressing/role/anonymous</wsa:To><wsse:Security><wsu:Timestamp wsu:Id=\"Timestamp-0fdb513d-1da4-4804-80b5-d04530653fac\"><wsu:Created>2017-03-22T14:23:00Z</wsu:Created><wsu:Expires>2017-03-22T14:28:00Z</wsu:Expires></wsu:Timestamp></wsse:Security></soap:Header><soap:Body><soap:Fault><faultcode>soap:Client</faultcode><faultstring>Business Rule Error</faultstring><faultactor>http://www.gateway.gov.uk/soap/2007/02/portal</faultactor><detail><GatewayDetails xmlns=\"urn:GSO-System-Services:external:SoapException\"><ErrorNumber>9001</ErrorNumber><Message>The service HMRC-AGENT-AGENT requires unique identifiers</Message><RequestID>0753B23CA0C14D23A4BBFC129795C42E</RequestID></GatewayDetails></detail></soap:Fault></soap:Body></soap:Envelope>"}	""")
  val invalidBadGatewayResponse: JsValue = Json.parse( """{"statusCode":502,"message":"<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/03/addressing\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"><soap:Header><wsa:Action>http://schemas.xmlsoap.org/ws/2004/03/addressing/fault</wsa:Action><wsa:MessageID>uuid:199814d0-9758-49d1-a2c0-d24300f67e2c</wsa:MessageID><wsa:RelatesTo>uuid:d1894fa0-b97d-4707-a814-e0c5ea79a01a</wsa:RelatesTo><wsa:To>http://schemas.xmlsoap.org/ws/2004/03/addressing/role/anonymous</wsa:To><wsse:Security><wsu:Timestamp wsu:Id=\"Timestamp-0fdb513d-1da4-4804-80b5-d04530653fac\"><wsu:Created>2017-03-22T14:23:00Z</wsu:Created><wsu:Expires>2017-03-22T14:28:00Z</wsu:Expires></wsu:Timestamp></wsse:Security></soap:Header><soap:Body><soap:Fault><faultcode>soap:Client</faultcode><faultstring>Business Rule Error</faultstring><faultactor>http://www.gateway.gov.uk/soap/2007/02/portal</faultactor><detail><GatewayDetails xmlns=\"urn:GSO-System-Services:external:SoapException\"><ErrorNumber>1111</ErrorNumber><Message>The service HMRC-AGENT-AGENT requires unique identifiers</Message><RequestID>0753B23CA0C14D23A4BBFC129795C42E</RequestID></GatewayDetails></detail></soap:Fault></soap:Body></soap:Envelope>"}	""")

  def testReviewDetailsController(reviewDetails: ReviewDetails): ReviewDetailsController = {
    val mockDataCacheConnector: DataCacheConnector = new DataCacheConnector(
      mockHttpClient,
      appConfig
    ) {
      override val sourceId: String = "Test"

      var reads: Int = 0

      override def fetchAndGetBusinessDetailsForSession(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Some[ReviewDetails]] = {
        reads = reads + 1
        Future.successful(Some(reviewDetails))
      }
    }
    new ReviewDetailsController(
      mockAuthConnector,
      mockBackLinkCache,
      appConfig,
      injectedViewInstanceNonUkAgent,
      injectedViewInstanceReviewDetails,
      injectedViewInstanceError,
      mockDataCacheConnector,
      mockAgentRegistrationService,
      mcc
    ) {
      override val controllerId = "test"
    }
  }

  def testReviewDetailsControllerNotFound: ReviewDetailsController = {
    val mockDataCacheConnector: DataCacheConnector = new DataCacheConnector(
      mockHttpClient,
      appConfig
    ) {
      override val sourceId: String = "Test"

      var reads: Int = 0

      override def fetchAndGetBusinessDetailsForSession(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[None.type] = {
        reads = reads + 1
        Future.successful(None)
      }
    }
    new ReviewDetailsController(
      mockAuthConnector,
      mockBackLinkCache,
      appConfig,
      injectedViewInstanceNonUkAgent,
      injectedViewInstanceReviewDetails,
      injectedViewInstanceError,
      mockDataCacheConnector,
      mockAgentRegistrationService,
      mcc
    ) {
      override val controllerId = "test"
    }
  }

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockBackLinkCache)
    reset(mockAgentRegistrationService)
  }

  "ReviewDetailsController" must {

    "show details" when {

      "unauthorised users" must {
        "respond with a redirect" in {
          businessDetailsWithUnAuthorisedUser { result =>
            status(result) must be(SEE_OTHER)
          }
        }

        "be redirected to the unauthorised page" in {
          businessDetailsWithUnAuthorisedUser { result =>
            redirectLocation(result).get must include("/business-customer/unauthorised")
          }
        }
      }
      "show error page if we have no review details with no exception" in {
        businessDetailsWithAuthorisedUserNotFound { result =>
          val document = Jsoup.parse(contentAsString(result))
          document.select("h1").text must be("Sorry, there is a problem with the service")
        }
      }

      "return Review Details view for a user, when user can't be directly found with login credentials" in {
        businessDetailsWithAuthorisedUser(nonDirectMatchReviewDetails) { result =>
          val document = Jsoup.parse(contentAsString(result))
          document.select("h1").text must include(messages("business-review.user.header"))
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be("/business-customer/business-verification/ATED/businessForm/LTD")
        }
      }

      "return Review Details view for a user, when we directly found this user" in {
        businessDetailsWithAuthorisedUser(directMatchReviewDetails) { result =>
          val document = Jsoup.parse(contentAsString(result))
          document.select("h1").text must include(messages("business-review.user.header"))
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED registration")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be("/business-customer/business-verification/ATED/businessForm/LTD")
        }
      }

      "return Review Details view for an agent, when agent can't be directly found with login credentials" in {
        businessDetailsWithAuthorisedAgent(nonDirectMatchReviewDetails) { result =>
          val document = Jsoup.parse(contentAsString(result))
          document.select("h1").text must include("Confirm your agency’s details")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED agency set up")
        }
      }

      "return Review Details view for an agent, when we directly found this agent" in {
        businessDetailsWithAuthorisedAgent(directMatchReviewDetails) { result =>
          val document = Jsoup.parse(contentAsString(result))
          document.select("h1").text must include("Confirm your agency’s details")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED agency set up")
        }
      }

      "return Review Details view for an agent, when we directly found this agent with editable address" in {
        businessDetailsWithAuthorisedAgent(directMatchReviewDetails.copy(isBusinessDetailsEditable = true)) { result =>
          val document = Jsoup.parse(contentAsString(result))
          document.select("h1").text must include("Check your agency details")
          document.getElementsByClass("govuk-caption-xl").text() must be("This section is: ATED agency set up")
        }
      }
    }


    "continue " must {

      "unauthorised users" must {
        "respond with a redirect" in {
          continueWithUnAuthorisedUser(service) { result =>
            status(result) must be(SEE_OTHER)
          }
        }

        "be redirected to the unauthorised page" in {
          continueWithUnAuthorisedUser(service) { result =>
            redirectLocation(result).get must include("/business-customer/unauthorised")
          }
        }
      }


      "Authorised Users" must {

        "enrolling for EMAC" when {

          "return service start page correctly for ATED Users" in {

            continueWithAuthorisedUser(service) {
              result =>
                status(result) must be(SEE_OTHER)
                redirectLocation(result).get must include("/ated-subscription/registered-business-address")
            }
          }

          "return service start page correctly for AWRS Users" in {

            continueWithAuthorisedUser("AWRS") {
              result =>
                status(result) must be(SEE_OTHER)
                redirectLocation(result).get must include("/alcohol-wholesale-scheme")
            }
          }

          "return agent registration page correctly for Agents" in {
            when(mockAgentRegistrationService.isAgentEnrolmentAllowed(ArgumentMatchers.eq(service))).thenReturn(true)
            continueWithAuthorisedAgentEMAC(service) {
              result =>
                status(result) must be(SEE_OTHER)
                redirectLocation(result).get must include("/ated-subscription/agent-confirmation")
            }
          }

          "return to error page for duplicate users" in {
            when(mockAgentRegistrationService.isAgentEnrolmentAllowed(ArgumentMatchers.eq(service))).thenReturn(true)
            continueWithDuplicategentEmac("ATED") {
              result =>
                status(result) must be(OK)
                val document = Jsoup.parse(contentAsString(result))
                document.title() must be("Somebody has already registered from your agency - GOV.UK")
            }
          }

          "return to error page for bad request" in {
            when(mockAgentRegistrationService.isAgentEnrolmentAllowed(ArgumentMatchers.eq(service))).thenReturn(true)
            continueWithBadRequestEACD("ATED") {
              result =>
                status(result) must be(OK)
                val document = Jsoup.parse(contentAsString(result))
                document.title() must be("Somebody has already registered from your agency - GOV.UK")
            }
          }

          "return to error page for wrong role users" in {
            when(mockAgentRegistrationService.isAgentEnrolmentAllowed(ArgumentMatchers.eq(service))).thenReturn(true)
            continueWithWrongRoleUserEmac("ATED") {
              result =>
                status(result) must be(OK)
                val document = Jsoup.parse(contentAsString(result))
                document.title() must be("You must be logged in as an administrator to submit an ATED return - GOV.UK")
            }
          }

          "respond with NotFound when invalid service is in uri" in {
            when(mockAgentRegistrationService.isAgentEnrolmentAllowed(ArgumentMatchers.eq("unknownServiceTest"))).thenReturn(false)
            intercept[NotFoundException] {
              continueWithAuthorisedUser("unknownServiceTest") {
              result =>
                status(result) must be(NOT_FOUND)
              }
            }
          }

          "throw an exception for any invalid user" in {
            when(mockAgentRegistrationService.isAgentEnrolmentAllowed(ArgumentMatchers.eq(service))).thenReturn(true)
            continueWithInvalidAgentEmac("ATED") {
              result =>
                val thrown = the[RuntimeException] thrownBy await(result)
                thrown.getMessage must include("We could not find your details. Check and try again.")
            }
          }
        }
      }
    }
  }

  private def fakeRequestWithSession(userId: String) = {
    val sessionId = s"session-${UUID.randomUUID}"
    FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value"))
  }

  private def continueWithUnAuthorisedUser(service: String)(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).continue(service).apply(fakeRequestWithSession(userId))
    test(result)
  }

  private def continueWithAuthorisedUser(service: String)(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).continue(service).apply(fakeRequestWithSession(userId))
    test(result)
  }



  private def continueWithAuthorisedAgentEMAC(service: String)(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)

    when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockAgentRegistrationService.enrolAgent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(CREATED, "")))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).continue(service).apply(fakeRequestWithSession(userId))
    test(result)
  }

  private def continueWithDuplicategentEmac(service: String)(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockAgentRegistrationService.enrolAgent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(HttpResponse(CONFLICT, "")))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).continue(service).apply(fakeRequestWithSession(userId))
    test(result)
  }

  private def continueWithBadRequestEACD(service: String)(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockAgentRegistrationService.enrolAgent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).continue(service).apply(fakeRequestWithSession(userId))
    test(result)
  }

  private def continueWithWrongRoleUserEmac(service: String)(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockAgentRegistrationService.enrolAgent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(HttpResponse(FORBIDDEN, "")))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).continue(service).apply(fakeRequestWithSession(userId))
    test(result)
  }


  private def continueWithInvalidAgentEmac(service: String)(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockAgentRegistrationService.enrolAgent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).continue(service).apply(fakeRequestWithSession(userId))
    test(result)
  }


  def businessDetailsWithAuthorisedAgent(reviewDetails: ReviewDetails)(test: Future[Result] => Any): ReviewDetailsController = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val testDetailsController = testReviewDetailsController(reviewDetails)
    val result = testDetailsController.businessDetails(service).apply(fakeRequestWithSession(userId))

    test(result)
    testDetailsController
  }

  def businessDetailsWithAuthorisedUser(reviewDetails: ReviewDetails)(test: Future[Result] => Any): ReviewDetailsController = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some("/business-customer/business-verification/ATED/businessForm/LTD")))
    val testDetailsController = testReviewDetailsController(reviewDetails)
    val result = testDetailsController.businessDetails(service).apply(fakeRequestWithSession(userId))

    test(result)
    testDetailsController
  }

  def businessDetailsWithAuthorisedUserNotFound(test: Future[Result] => Any): ReviewDetailsController = {
    val userId = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val testDetailsController = testReviewDetailsControllerNotFound
    val result = testDetailsController.businessDetails(service).apply(fakeRequestWithSession(userId))

    test(result)
    testDetailsController
  }

  def businessDetailsWithUnAuthorisedUser(test: Future[Result] => Any): Unit = {
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val result = testReviewDetailsController(nonDirectMatchReviewDetails).businessDetails(service).apply(fakeRequestWithSession(userId))

    test(result)
  }

}
