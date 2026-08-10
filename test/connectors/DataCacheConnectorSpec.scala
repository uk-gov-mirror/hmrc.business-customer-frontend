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

package connectors

import config.ApplicationConfig
import models.{Address, BusinessRegistration, ReviewDetails}
import org.mockito.Mockito.when
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, SessionId}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DataCacheConnectorSpec extends PlaySpec with GuiceOneServerPerSuite {

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))


  val appConfig = app.injector.instanceOf[ApplicationConfig]


  class Setup extends ConnectorTest {
    val connector: DataCacheConnector = new DataCacheConnector(mockHttpClient, appConfig)
  }

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  "DataCacheConnector" must {

    "fetchAndGetBusinessDetailsForSession" must {
      "fetch saved BusinessDetails from SessionCache" in new Setup {
        val reviewDetails: ReviewDetails =
          ReviewDetails("ACME", Some("UIB"), Address("line1", "line2", None, None, None, "country"), "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))
        when(executeGet[CacheMap]).thenReturn(Future.successful(CacheMap("test", Map("BC_Business_Details" -> Json.toJson(reviewDetails)))))

        val result: Future[Option[ReviewDetails]] = connector.fetchAndGetBusinessDetailsForSession
          await(result) must be(Some(reviewDetails))
      }
    }

    "fetchAndGetBusinessRegistrationDetailsForSession" must {
      "fetch saved RegistrationDetails from SessionCache" in new Setup {
        val registrationDetails: BusinessRegistration =
          BusinessRegistration("ACME", Address("line1", "line2", None, None, None, "country"))
        when(executeGet[CacheMap]).thenReturn(Future.successful(CacheMap("test", Map("BC_Business_Details_form_payload" -> Json.toJson(registrationDetails)))))

        val result: Future[Option[BusinessRegistration]] = connector.fetchAndGetBusinessRegistrationDetailsForSession
          await(result) must be(Some(registrationDetails))
      }
    }

    "saveAndReturnBusinessDetails" must {

      "save the fetched business details" in new Setup {
        val reviewDetails: ReviewDetails = ReviewDetails("ACME", Some("UIB"), Address("line1", "line2", None, None, None, "country"), "sap123", "safe123", isAGroup = false, directMatch = false, Some("agent123"))
        val inputBody: JsValue = Json.toJson(reviewDetails)

        when(executePut[CacheMap](inputBody)).thenReturn(Future.successful(CacheMap("test", Map("BC_Business_Details" -> Json.toJson(reviewDetails)))))

        val result: Future[Option[ReviewDetails]] = connector.saveReviewDetails(reviewDetails)
        await(result).get must be(reviewDetails)
      }

    }
    "saveBusinessRegistrationDetails" must {

      "save the business registration details" in new Setup {
        val reviewDetails: BusinessRegistration = BusinessRegistration("ACME", Address("line1", "line2", None, None, None, "country"))
        val inputBody: JsValue = Json.toJson(reviewDetails)

        when(executePut[CacheMap](inputBody)).thenReturn(
          Future.successful(CacheMap("test", Map("BC_Business_Details_form_payload" -> Json.toJson(reviewDetails)))))

        val result: Future[Option[BusinessRegistration]] = connector.saveBusinessRegistrationDetails(reviewDetails)
        await(result).get must be(reviewDetails)
      }
    }

    "clearCache" must {
      "clear the cache for the session" in new Setup{
        when(executeDelete[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, "")))

        val result: Future[Unit] = connector.clearCache
        await(result) must be(())
      }
    }
  }
}
