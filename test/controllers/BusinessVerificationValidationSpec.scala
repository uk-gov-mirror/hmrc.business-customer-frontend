/*
 * Copyright 2024 HM Revenue & Customs
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

import builders.AuthBuilder
import config.ApplicationConfig
import connectors.{BackLinkCacheConnector, BusinessRegCacheConnector}
import forms.{BusinessName, SoleTraderName, Utr}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Headers, MessagesControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import services.BusinessMatchingService
import uk.gov.hmrc.auth.core.AuthConnector
import views.html._

import java.util.UUID
import scala.concurrent.Future

class BusinessVerificationValidationSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with Injecting with BusinessMatchTestObjects {

  val request: FakeRequest[_]                              = FakeRequest()
  val mockBusinessMatchingService: BusinessMatchingService =
    mock[BusinessMatchingService]
  val mockAuthConnector: AuthConnector                         = mock[AuthConnector]
  val mockBackLinkCache: BackLinkCacheConnector                = mock[BackLinkCacheConnector]
  val mockBusinessRegCacheConnector: BusinessRegCacheConnector =
    mock[BusinessRegCacheConnector]
  val service = "ATED"

  val mockReviewDetailsController: ReviewDetailsController =
    mock[ReviewDetailsController]
  val injectedViewInstanceBusinessUtr: generic_business_utr =
    inject[views.html.generic_business_utr]

  val mockBusinessVerificationController: BusinessVerificationController = mock[BusinessVerificationController]

  val appConfig: ApplicationConfig               = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents =
    inject[MessagesControllerComponents]

  class Setup {
    val controller: BusinessUtrController =
      new BusinessUtrController(
        appConfig,
        mockAuthConnector,
        injectedViewInstanceBusinessUtr,
        mockBusinessRegCacheConnector,
        mockBackLinkCache,
        mockBusinessMatchingService,
        mockReviewDetailsController,
        mockBusinessVerificationController,
        mcc
      ) {
        override val controllerId = "test"
      }
  }
  def matchSuccessResponse(businessType: String): JsValue = businessType match {
    case "UIB"  => matchSuccessResponseUIB
    case "LLP"  => matchSuccessResponseLLP
    case "OBP"  => matchSuccessResponseOBP
    case "NRL"  => matchSuccessResponseNRL
    case "LTD"  => matchSuccessResponseLTD
    case "LP"   => matchSuccessResponseLP
    case "UT"   => matchSuccessResponseLTD
    case "ULTD" => matchSuccessResponseLTD
    case "SOP" => matchSuccessResponseSOP
  }

  val matchSuccessResponseSOP: JsValue = Json.parse("""
      |{
      |  "businessName": "ACME",
      |  "businessType": "Sole Trader",
      |  "businessAddress": {
      |    "line_1": "line 1",
      |    "line_2": "line 2",
      |    "line_3": "line 3",
      |    "line_4": "line 4",
      |    "postcode": "AA1 1AA",
      |    "country": "GB"
      |  },
      |  "sapNumber": "sap123",
      |  "safeId": "safe123",
      |  "isAGroup": false,
      |  "directMatch" : false,
      |  "agentReferenceNumber": "agent123",
      |  "isBusinessDetailsEditable": false
      |}
    """.stripMargin)

  val matchSuccessResponseUIB: JsValue = Json.parse("""
      |{
      |  "businessName": "ACME",
      |  "businessType": "Unincorporated body",
      |  "businessAddress": {
      |    "line_1": "line 1",
      |    "line_2": "line 2",
      |    "line_3": "line 3",
      |    "line_4": "line 4",
      |    "postcode": "AA1 1AA",
      |    "country": "GB"
      |  },
      |  "sapNumber": "sap123",
      |  "safeId": "safe123",
      |  "isAGroup": false,
      |  "directMatch" : false,
      |  "agentReferenceNumber": "agent123",
      |  "isBusinessDetailsEditable": false
      |}
    """.stripMargin)

  val matchSuccessResponseLTD: JsValue = Json.parse("""
      |{
      |  "businessName": "ACME",
      |  "businessType": "Limited company",
      |  "businessAddress": {
      |    "line_1": "line 1",
      |    "line_2": "line 2",
      |    "line_3": "line 3",
      |    "line_4": "line 4",
      |    "postcode": "AA1 1AA",
      |    "country": "GB"
      |  },
      |  "sapNumber": "sap123",
      |  "safeId": "safe123",
      |  "isAGroup": true,
      |  "directMatch" : false,
      |  "agentReferenceNumber": "agent123",
      |  "isBusinessDetailsEditable": false
      |}
    """.stripMargin)

  val matchSuccessResponseNRL: JsValue = Json.parse("""
      |{
      |  "businessName": "ACME",
      |  "businessType": "Limited company",
      |  "businessAddress": {
      |    "line_1": "line 1",
      |    "line_2": "line 2",
      |    "line_3": "line 3",
      |    "line_4": "line 4",
      |    "postcode": "AA1 1AA",
      |    "country": "GB"
      |  },
      |  "sapNumber": "sap123",
      |  "safeId": "safe123",
      |  "isAGroup": false,
      |  "directMatch" : false,
      |  "agentReferenceNumber": "agent123",
      |  "isBusinessDetailsEditable": false
      |}
    """.stripMargin)

  val matchSuccessResponseOBP: JsValue = Json.parse("""
      |{
      |  "businessName": "ACME",
      |  "businessType": "Ordinary business partnership",
      |  "businessAddress": {
      |    "line_1": "line 1",
      |    "line_2": "line 2",
      |    "line_3": "line 3",
      |    "line_4": "line 4",
      |    "postcode": "AA1 1AA",
      |    "country": "GB"
      |  },
      |  "sapNumber": "sap123",
      |  "safeId": "safe123",
      |  "isAGroup": false,
      |  "directMatch" : false,
      |  "agentReferenceNumber": "agent123",
      |  "isBusinessDetailsEditable": false
      |}
    """.stripMargin)

  val matchSuccessResponseLLP: JsValue = Json.parse("""
      |{
      |  "businessName": "ACME",
      |  "businessType": "Limited liability partnership",
      |  "businessAddress": {
      |    "line_1": "line 1",
      |    "line_2": "line 2",
      |    "line_3": "line 3",
      |    "line_4": "line 4",
      |    "postcode": "AA1 1AA",
      |    "country": "GB"
      |  },
      |  "sapNumber": "sap123",
      |  "safeId": "safe123",
      |  "isAGroup": false,
      |  "directMatch" : false,
      |  "agentReferenceNumber": "agent123",
      |  "isBusinessDetailsEditable": false
      |}
    """.stripMargin)

  val matchSuccessResponseLP: JsValue = Json.parse("""
      |{
      |  "businessName": "ACME",
      |  "businessType": "Limited partnership",
      |  "businessAddress": {
      |    "line_1": "line 1",
      |    "line_2": "line 2",
      |    "line_3": "line 3",
      |    "line_4": "line 4",
      |    "postcode": "AA1 1AA",
      |    "country": "GB"
      |  },
      |  "sapNumber": "sap123",
      |  "safeId": "safe123",
      |  "isAGroup": true,
      |  "directMatch" : false,
      |  "agentReferenceNumber": "agent123",
      |  "isBusinessDetailsEditable": false
      |}
    """.stripMargin)

  val matchFailureResponse: JsValue = Json.parse(
    """{"reason":"Sorry. Business details not found. Try with correct UTR and/or name."}"""
  )

  "BusinessVerificationValidationController" must {

    "CO Tax UTR must not be empty" in new Setup {
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[BusinessName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Some(BusinessName("ACME"))))

      submitWithAuthorisedUserJson(
        controller,
        "LTD",
        Map("utr" -> ""),
        service
      ) { result =>
        status(result) must be(BAD_REQUEST)
        contentAsString(result) must include(s"Enter a Corporation Tax Unique Taxpayer Reference")
      }
    }
    "SA Tax UTR must not be empty" in new Setup {
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[SoleTraderName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Some(SoleTraderName("John", "Doe"))))
      submitWithAuthorisedSaUserJson(
        controller,
        Map("utr" -> ""),
        "AWRS"
      ) { result =>
        status(result) must be(BAD_REQUEST)
        contentAsString(result) must include(s"Enter a Self Assessment Unique Taxpayer Reference")
      }
    }

    "handle the utr submission validation correctly" must {
      "if the selection is an Organisation" must {
        formValidationInputUTRDataSetOrg foreach { dataSet =>
          s"${dataSet._1}" must {
            dataSet._2 foreach { inputData =>
              s"${inputData._1}" in new Setup {
                submitWithAuthorisedUserSuccessOrg(
                  inputData._2,
                  inputData._3,
                  controller
                ) { result =>
                  status(result) must be(BAD_REQUEST)
                  contentAsString(result) must include(s"${inputData._4}")
                }
              }
            }
          }
        }
      }

      "if the selection is Sole Trader" must {
        formValidationInputUtrDataSetInd foreach { dataSet =>
          s"${dataSet._1}" in new Setup {
            submitWithAuthorisedUserSuccessIndividual(
              dataSet._3,
              controller
            ) { result =>
              status(result) must be(BAD_REQUEST)
              contentAsString(result) must include(s"${dataSet._4}")
            }
          }
        }
      }
    }

    "if the Ordinary Business Partnership form is successfully validated:" must {
      "for successful match, status should be 303 and user should be redirected to review details page" in new Setup {

        submitWithAuthorisedUserSuccessOrg(
          "OBP",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody(
              "utr" -> s"1111111111"
            ),
          controller,
          "ATED"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(s"/business-customer/review-details/$service")

        }
      }
      "for successful match, status should be 303 and user should be redirected to review details page for AWRS" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "OBP",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody(
              "utr" -> s"$matchUtr"
            ),
          controller,
          "awrs"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/awrs"
          )
        }
      }

      "for successful match, status should be 303 and user should be redirected to review details page for neither AWRS nor ATED journey" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "OBP",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody("utr" -> s"$matchUtr"),
          controller,
          "amls"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(s"/business-customer/review-details/amls")
        }
      }

      "for unsuccessful match, status should be Redirect and user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "OBP",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/OBP"
          )
        }
      }
    }
    "return an error if no cached data is found for business Name" in new Setup {
      submitWithAuthorisedUserOrgNoCachedName(
        "OBP",
        FakeRequest("POST", "/").withFormUrlEncodedBody(
          "utr" -> s"$matchUtr"
        ),
        controller
      ) { result =>
        status(result) must be(INTERNAL_SERVER_ERROR)
        }
      }
    "return an error if no cached data is found for Sole Trader Name" in new Setup {
      val service = "AWRS"
        submitWithAuthorisedSaUserNoCachedName(
        "SOP",
        FakeRequest("POST", "/").withFormUrlEncodedBody(
          "utr" -> s"$matchUtr"
        ),
        controller,
          service
        ) { result =>
          status(result) must be(INTERNAL_SERVER_ERROR)
        }
    }
    "if the Limited Liability Partnership form is successfully validated:" must {
      "for successful match, status should be 303 and  user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LLP",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/$service"
          )
        }
      }
      "for successful match, status should be 303 and  user should be redirected to review details page for AWRS" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LLP",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody(
              "utr" -> s"$matchUtr"
            ),
          controller,
          "awrs"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/awrs"
          )
        }
      }
      "for successful match, status should be 303 and  user should be redirected to review details page for neither AWRS nor ATED journey" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LLP",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody("utr" -> s"$matchUtr"),
          controller,
          "amls"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(s"/business-customer/review-details/amls")
        }
      }
      "for unsuccessful match, status should be Redirect and user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "LLP",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/LLP"
          )
        }
      }
    }

    "if the Limited Partnership form is successfully validated:" must {
      "for successful match, status should be 303 and user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LP",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/$service"
          )
        }
      }
      "for successful match, status should be 303 and user should be redirected to review details page for AWRS" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LP",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody(
              "utr" -> s"$matchUtr"
            ),
          controller,
          "awrs"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/awrs"
          )
        }
      }

      "for successful match, status should be 303 and  user should be redirected to review details page for neither ATED nor AWRS journey" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LP",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody("utr" -> s"$matchUtr"),
          controller,
          "amls"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(s"/business-customer/review-details/amls")
        }
      }

      "for unsuccessful match, status should be Redirect and user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "LP",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/LP"
          )
        }
      }
    }

    "if the Sole Trader form  is successfully validated:" must {
      "for successful match, status should be 303 and  user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessIndividual(
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/AWRS"
          )
        }
      }
      "for successful match, status should be 303 and  user should be redirected to review details page for AWRS" in new Setup {
        submitWithAuthorisedUserSuccessIndividual(
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody(
              "utr" -> s"$matchUtr"
            ),
          controller,
          "awrs"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/awrs"
          )
        }
      }

      "for successful match, status should be 303 and  user should be redirected to review details page for neither AWRS nor ATED journey" in new Setup {
        submitWithAuthorisedUserSuccessIndividual(
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody("utr" -> s"$matchUtr"),
          controller,
          "amls"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(s"/business-customer/review-details/amls")
        }
      }

      "for unsuccessful match, status should be Redirect and user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailureIndividual(
          "SOP",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            Map(
              "utr" -> s"$noMatchUtr"
            ).toSeq: _*
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/SOP"
          )
        }
      }
    }

    "if the Non Resident Landlord is successfully validated:" must {
      "for successful match, status should be 303 and  user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "NRL",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/$service"
          )
        }
      }

      "for successful match with missing countryCode, status should be 303 and  user should be redirected to update overseas details reg" in new Setup {
        submitWithAuthorisedUserSuccessOrgNRLNoCountry(
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          // status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/register/$service/NRL"
          )
          verify(mockBusinessRegCacheConnector, times(1)).cacheDetails(
            ArgumentMatchers.eq("Update_No_Register"),
            ArgumentMatchers.eq(true)
          )(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )
        }
      }

      "for unsuccessful match, status should be Redirect and  user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "NRL",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/NRL"
          )
        }
      }
    }

    "if the Unincorporated body form  is successfully validated:" must {
      "for successful match, status should be 303 and  user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "UIB",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/$service"
          )
        }
      }
      "for successful match, status should be 303 and  user should be redirected to review details page for AWRS" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "UIB",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody(
              "utr" -> s"$matchUtr"
            ),
          controller,
          "awrs"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/awrs"
          )
        }
      }
      "for successful match, status should be 303 and  user should be redirected to review details page for neither AWRS nor ATED journey" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "UIB",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody("utr" -> s"$matchUtr"),
          controller,
          "amls"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(s"/business-customer/review-details/amls")
        }
      }
      "for unsuccessful match, status should be Redirect and  user should be on same details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "UIB",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/UIB"
          )
        }
      }
    }

    "if the Limited Company form  is successfully validated:" must {
      "for successful match, status should be 303 and  user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LTD",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/$service"
          )
        }
      }
      "for successful match, status should be 303 and  user should be redirected to review details page for AWRS" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LTD",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody(
              "utr" -> s"$matchUtr"
            ),
          controller,
          "awrs"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/awrs"
          )
        }
      }

      "for successful match, status should be 303 and  user should be redirected to review details page for neither AWRS nor ATED journey" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "LTD",
          FakeRequest("POST", "/")
            .withFormUrlEncodedBody("utr" -> s"$matchUtr"),
          controller,
          "amls"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(s"/business-customer/review-details/amls")
        }
      }

      "for unsuccessful match, status should be Redirect and user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "LTD",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/LTD"
          )
        }
      }
    }

    "if the Unit Trust form  is successfully validated:" must {
      "for successful match, status should be 303 and  user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "UT",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/$service"
          )
        }
      }
      "for unsuccessful match, status should be Redirect and user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "UT",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/UT"
          )
        }
      }
    }

    "if the Unlimited Company form  is successfully validated:" must {
      "for successful match, status should be 303 and  user should be redirected to review details page" in new Setup {
        submitWithAuthorisedUserSuccessOrg(
          "ULTD",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$matchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/review-details/$service"
          )
        }
      }
      "for unsuccessful match, status should be Redirect and user should be on details not found page" in new Setup {
        submitWithAuthorisedUserFailure(
          "ULTD",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            "utr" -> s"$noMatchUtr"
          ),
          controller
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-verification/$service/detailsNotFound/ULTD"
          )
        }
      }
    }

    def submitWithAuthorisedUserSuccessOrg(
      businessType: String,
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      controller: BusinessUtrController,
      service: String = service
    )(test: Future[Result] => Any): Unit = {
      val sessionId = s"session-${UUID.randomUUID}"
      val userId    = s"user-${UUID.randomUUID}"

      builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[BusinessName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Some(BusinessName("ACME"))))
      when(
        mockBusinessMatchingService.matchBusinessWithOrganisationName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(matchSuccessResponse(businessType)))
      when(
        mockBusinessRegCacheConnector.cacheDetails[Utr](ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Utr("1111111111")))

      val fullReq = fakeRequest
        .withSession(
          "sessionId" -> sessionId,
          "token"     -> "RANDOMTOKEN",
          "userId"    -> userId
        )
        .withHeaders(Headers("Authorization" -> "value"))

      val result = controller.submit(service, businessType).apply(fullReq)

      test(result)
    }

    def submitWithAuthorisedUserOrgNoCachedName(
      businessType: String,
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      controller: BusinessUtrController,
      service: String = service
    )(test: Future[Result] => Any): Unit = {
      val sessionId = s"session-${UUID.randomUUID}"
      val userId    = s"user-${UUID.randomUUID}"

      builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

      when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[BusinessName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(None))
      when(
        mockBusinessMatchingService.matchBusinessWithOrganisationName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(matchSuccessResponse(businessType)))
      when(
        mockBusinessRegCacheConnector.cacheDetails[Utr](ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Utr("1111111111")))

      val fullReq = fakeRequest
        .withSession(
          "sessionId" -> sessionId,
          "token"     -> "RANDOMTOKEN",
          "userId"    -> userId
        )
        .withHeaders(Headers("Authorization" -> "value"))

      val result = controller.submit(service, businessType).apply(fullReq)

      test(result)
    }
    def submitWithAuthorisedSaUserNoCachedName(
      businessType: String,
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      controller: BusinessUtrController,
      service: String
    )(test: Future[Result] => Any): Unit = {
      val sessionId = s"session-${UUID.randomUUID}"
      val userId    = s"user-${UUID.randomUUID}"

      builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)

      when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[SoleTraderName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(None))
      when(
        mockBusinessMatchingService.matchBusinessWithOrganisationName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(matchSuccessResponse(businessType)))
      when(
        mockBusinessRegCacheConnector.cacheDetails[Utr](ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Utr("1111111111")))

      val fullReq = fakeRequest
        .withSession(
          "sessionId" -> sessionId,
          "token"     -> "RANDOMTOKEN",
          "userId"    -> userId
        )
        .withHeaders(Headers("Authorization" -> "value"))

      val result = controller.submit(service, businessType).apply(fullReq)

      test(result)
    }

    def submitWithAuthorisedUserSuccessOrgNRLNoCountry(
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      controller: BusinessUtrController
    )(test: Future[Result] => Any): Unit = {
      val sessionId = s"session-${UUID.randomUUID}"
      val userId    = s"user-${UUID.randomUUID}"

      builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
      when(
        mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(None))
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[BusinessName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Some(BusinessName("ACME"))))

      val matchSuccessResponse = Json.parse("""
        |{
        |  "businessName": "ACME",
        |  "businessType": "Limited company",
        |  "businessAddress": {
        |    "line_1": "line 1",
        |    "line_2": "line 2",
        |    "line_3": "line 3",
        |    "line_4": "line 4",
        |    "postcode": "AA1 1AA",
        |    "country": ""
        |  },
        |  "sapNumber": "sap123",
        |  "safeId": "safe123",
        |  "isAGroup": false,
        |  "directMatch" : false,
        |  "agentReferenceNumber": "agent123",
        |  "isBusinessDetailsEditable": false
        |}
    """.stripMargin)

      when(
        mockBusinessMatchingService.matchBusinessWithOrganisationName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(matchSuccessResponse))

      val fullReq = fakeRequest
        .withSession(
          "sessionId" -> sessionId,
          "token"     -> "RANDOMTOKEN",
          "userId"    -> userId
        )
        .withHeaders(Headers("Authorization" -> "value"))

      val result = controller.submit(service, "NRL").apply(fullReq)

      test(result)
    }

    def submitWithAuthorisedUserSuccessIndividual(
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      controller: BusinessUtrController,
      service: String = "AWRS"
    )(test: Future[Result] => Any): Unit = {
      val sessionId = s"session-${UUID.randomUUID}"
      val userId    = s"user-${UUID.randomUUID}"

      builders.AuthBuilder.mockAuthorisedSaUser(userId, mockAuthConnector)
      when(
        mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(None))
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[SoleTraderName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Some(SoleTraderName("John", "Doe"))))
      when(
        mockBusinessMatchingService.matchBusinessWithIndividualName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      )
        .thenReturn(Future.successful(matchSuccessResponseSOP))
      when(
        mockBusinessRegCacheConnector.cacheDetails[Utr](ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Utr("1111111111")))

      val result = controller
        .submit(service, "SOP")
        .apply(
          fakeRequest
            .withSession(
              "sessionId" -> sessionId,
              "token"     -> "RANDOMTOKEN",
              "userId"    -> userId
            )
            .withHeaders(Headers("Authorization" -> "value"))
        )

      test(result)
    }

    def submitWithAuthorisedUserFailure(
      businessType: String,
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      controller: BusinessUtrController
    )(test: Future[Result] => Any): Unit = {
      val sessionId = s"session-${UUID.randomUUID}"
      val userId    = s"user-${UUID.randomUUID}"

      builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
      when(
        mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(None))

      when(
        mockBusinessMatchingService.matchBusinessWithOrganisationName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      )
        .thenReturn(Future.successful(matchFailureResponse))

      val result = controller
        .submit(service, businessType)
        .apply(
          fakeRequest
            .withSession(
              "sessionId" -> sessionId,
              "token"     -> "RANDOMTOKEN",
              "userId"    -> userId
            )
            .withHeaders(Headers("Authorization" -> "value"))
        )

      test(result)
    }

    def submitWithAuthorisedUserFailureIndividual(
      businessType: String,
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      controller: BusinessUtrController
    )(test: Future[Result] => Any): Unit = {
      val sessionId = s"session-${UUID.randomUUID}"
      val userId    = s"user-${UUID.randomUUID}"

      builders.AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
      when(
        mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(None))
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[SoleTraderName](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(Some(SoleTraderName("John", "Doe"))))
      when(
        mockBusinessMatchingService.matchBusinessWithIndividualName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      )
        .thenReturn(Future.successful(matchFailureResponse))

      val result = controller
        .submit(service, businessType)
        .apply(
          fakeRequest
            .withSession(
              "sessionId" -> sessionId,
              "token"     -> "RANDOMTOKEN",
              "userId"    -> userId
            )
            .withHeaders(Headers("Authorization" -> "value"))
        )

      test(result)
    }

    def submitWithAuthorisedUserJson(
      controller: BusinessUtrController,
      businessType: String,
      fields: Map[String, String],
      service: String
    )(test: Future[Result] => Any): Unit = {
      val sessionId            = s"session-${UUID.randomUUID}"
      val userId               = s"user-${UUID.randomUUID}"

      def generateRequest: FakeRequest[AnyContentAsFormUrlEncoded] = {
        FakeRequest("POST", "/")
          .withSession(
            "sessionId" -> sessionId,
            "token"     -> "RANDOMTOKEN",
            "userId"    -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
          .withFormUrlEncodedBody(fields.toSeq: _*)
      }
      if (businessType.equalsIgnoreCase("SOP")) {
        AuthBuilder.mockAuthorisedSaUser(userId, mockAuthConnector)
      } else { AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector) }

      when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))
      when(
        mockBusinessRegCacheConnector.fetchAndGetCachedDetails[BusinessName](ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Some(BusinessName("ACME"))))
      when(
        mockBusinessMatchingService.matchBusinessWithOrganisationName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      )
        .thenReturn(Future.successful(matchSuccessResponse(businessType)))
      when(
        mockBusinessRegCacheConnector.cacheDetails[Utr](ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Utr(("1111111111"))))

      val result = controller.submit(service, businessType).apply(generateRequest)

      test(result)
    }
    def submitWithAuthorisedSaUserJson(
      controller: BusinessUtrController,
      fields: Map[String, String],
      service: String
    )(test: Future[Result] => Any): Unit = {
      val sessionId                                                = s"session-${UUID.randomUUID}"
      val userId                                                   = s"user-${UUID.randomUUID}"
      def generateRequest: FakeRequest[AnyContentAsFormUrlEncoded] = {
        FakeRequest("POST", "/")
          .withSession(
            "sessionId" -> sessionId,
            "token"     -> "RANDOMTOKEN",
            "userId"    -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
          .withFormUrlEncodedBody(fields.toSeq: _*)
      }
      AuthBuilder.mockAuthorisedSaUser(userId, mockAuthConnector)

      when(
        mockBusinessMatchingService.matchBusinessWithOrganisationName(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      )
        .thenReturn(Future.successful(matchSuccessResponseSOP))
      when(
        mockBusinessRegCacheConnector.cacheDetails[Utr](ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn(Future.successful(Utr(("1111111111"))))

      val result = controller.submit("AWRS", "SOP").apply(generateRequest)

      test(result)
    }
  }
}
