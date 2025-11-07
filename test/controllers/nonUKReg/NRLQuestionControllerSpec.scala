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

import builders.SessionBuilder
import config.ApplicationConfig
import connectors.{BackLinkCacheConnector, BusinessRegCacheConnector}
import models.NRLQuestion
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.mvc.{AnyContentAsFormUrlEncoded, Headers, MessagesControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.NotFoundException
import views.html.nonUkReg.nrl_question

import scala.concurrent.Future


class NRLQuestionControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with Injecting {

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBackLinkCache: BackLinkCacheConnector = mock[BackLinkCacheConnector]
  val mockPaySAController: PaySAQuestionController = mock[PaySAQuestionController]
  val mockBusRegController: BusinessRegController = mock[BusinessRegController]
  val service = "amls"
  val invalidService = "scooby-doo"
  val mockBusinessRegistrationCache: BusinessRegCacheConnector = mock[BusinessRegCacheConnector]
  val injectedViewInstance: nrl_question = inject[views.html.nonUkReg.nrl_question]

  val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]

  object TestNRLQuestionController extends NRLQuestionController(
    mockAuthConnector,
    mockBackLinkCache,
    appConfig,
    injectedViewInstance,
    mockBusRegController,
    mcc,
    mockPaySAController,
    mockBusinessRegistrationCache
  ) {
    override val controllerId = "test"
  }

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockBackLinkCache)
  }

  "NRLQuestionController" must {

    "view" must {

      "respond with NotFound when invalid service is in uri" in {
        intercept[NotFoundException] {
          viewWithAuthorisedClient(invalidService) { result =>
            status(result) must be(NOT_FOUND)
          }
        }
      }

      "redirect present user to NRL question page, if user is not an agent" in {
        viewWithAuthorisedClient(service) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))
          document.title must be("Are you a non-resident landlord? - Register as an alcohol wholesaler or producer - GOV.UK")
        }
      }

      "Show the NRL page with saved data if we have some" in {
        viewWithAuthorisedClientWithSavedData(service) { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))
          document.title must be("Are you a non-resident landlord? - Register as an alcohol wholesaler or producer - GOV.UK")
          document.select(".govuk-radios__item").text() must include("Yes")
          document.select(".govuk-radios__item").text() must include("No")
          document.getElementById("paysSA-2").outerHtml() must include("checked")
        }
      }

      "redirect to register non-uk page, if user is an agent" in {
        when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
        viewWithAuthorisedAgent(service) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some(s"/business-customer/register/$service/NUK"))
        }
      }
    }

    "continue" must {

      "respond with NotFound when invalid service is in uri" in {
        val fakeRequest = FakeRequest("POST", "/").withFormUrlEncodedBody(Map("paysSA" -> "").toSeq: _*)
        intercept[NotFoundException] {
          continueWithAuthorisedClient(fakeRequest, invalidService) { result =>
            status(result) must be(NOT_FOUND)
          }
        }
      }

      "if user doesn't select any radio button, show form error with bad_request" in {
        val fakeRequest = FakeRequest("POST", "/").withFormUrlEncodedBody(Map("paysSA" -> "").toSeq: _*)
        continueWithAuthorisedClient(fakeRequest, service) { result =>
          status(result) must be(BAD_REQUEST)
        }
      }
      "if user select 'yes', redirect it to Pay SA page" in {
        val fakeRequest = FakeRequest("POST", "/").withFormUrlEncodedBody(Map("paysSA" -> "true").toSeq: _*)
        continueWithAuthorisedClient(fakeRequest, service) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some(s"/business-customer/register/non-uk-client/paySA/$service"))
        }
      }
      "if user select 'no', redirect it to business registration page" in {
        val fakeRequest = FakeRequest("POST", "/").withFormUrlEncodedBody(Map("paysSA" -> "false").toSeq: _*)
        continueWithAuthorisedClient(fakeRequest, service) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some(s"/business-customer/register/$service/NUK"))

        }
      }

    }
  }

  def viewWithAuthorisedAgent(serviceName: String)(test: Future[Result] => Any): Any = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestNRLQuestionController.view(serviceName).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }


  def viewWithAuthorisedClient(serviceName: String)(test: Future[Result] => Any): Any = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[String](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    val result = TestNRLQuestionController.view(serviceName).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }


  def viewWithAuthorisedClientWithSavedData(serviceName: String)(test: Future[Result] => Any): Any = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"
    val successModel = NRLQuestion(Some(false))

    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockBusinessRegistrationCache.fetchAndGetCachedDetails[NRLQuestion](ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(successModel)))

    val result = TestNRLQuestionController.view(serviceName).apply(FakeRequest().withSession(
      "sessionId" -> sessionId,
      "token" -> "RANDOMTOKEN",
      "userId" -> userId)
      .withHeaders(Headers("Authorization" -> "value")))

    test(result)
  }


  def continueWithAuthorisedClient(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded], serviceName: String)(test: Future[Result] => Any): Any = {
    val userId   = s"user-${UUID.randomUUID}"
    builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
    when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

    val result = TestNRLQuestionController.continue(serviceName).apply(SessionBuilder.updateRequestWithSession(fakeRequest, userId))
    test(result)
  }

}
