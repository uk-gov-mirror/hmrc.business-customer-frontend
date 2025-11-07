/*
 * Copyright 2025 HM Revenue & Customs
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
import controllers.nonUKReg.{BusinessRegController, NRLQuestionController}
import forms._
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.i18n.{Lang, Messages}
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import services.BusinessMatchingService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.NotFoundException
import views.html._

import java.util.UUID
import scala.concurrent.Future

class BusinessVerificationControllerSpec
    extends PlaySpec
    with GuiceOneServerPerSuite
    with MockitoSugar
    with Injecting {

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBusinessMatchingService: BusinessMatchingService =
    mock[BusinessMatchingService]
  val mockBackLinkCache: BackLinkCacheConnector = mock[BackLinkCacheConnector]
  val mockBusinessRegCacheConnector: BusinessRegCacheConnector =
    mock[BusinessRegCacheConnector]
  val service = "ATED"
  val invalidService = "scooby-doo"

  val appConfig: ApplicationConfig = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents =
    inject[MessagesControllerComponents]
  implicit val messages: Messages =
    mcc.messagesApi.preferred(Seq(Lang.defaultLang))

  val businessRegUKController: BusinessRegUKController =
    mock[BusinessRegUKController]
  val busRegController: BusinessRegController = mock[BusinessRegController]
  val nrlQuestionController: NRLQuestionController = mock[NRLQuestionController]
  val reviewDetailsController: ReviewDetailsController =
    mock[ReviewDetailsController]
  val homeController: HomeController = mock[HomeController]
  val injectedViewInstance: business_verification =
    inject[views.html.business_verification]
  val injectedViewInstanceSOP: business_lookup_SOP =
    inject[views.html.business_lookup_SOP]
  val injectedViewInstanceLTD: business_lookup_LTD =
    inject[views.html.business_lookup_LTD]
  val injectedViewInstanceUIB: business_lookup_UIB =
    inject[views.html.business_lookup_UIB]
  val injectedViewInstanceOBP: business_lookup_OBP =
    inject[views.html.business_lookup_OBP]
  val injectedViewInstanceLLP: business_lookup_LLP =
    inject[views.html.business_lookup_LLP]
  val injectedViewInstanceLP: business_lookup_LP =
    inject[views.html.business_lookup_LP]
  val injectedViewInstanceNRL: business_lookup_NRL =
    inject[views.html.business_lookup_NRL]
  val injectedViewInstanceDetailsNotFound: details_not_found =
    inject[views.html.details_not_found]

  class Setup {
    val controller: BusinessVerificationController =
      new BusinessVerificationController(
        appConfig,
        mockAuthConnector,
        injectedViewInstance,
        injectedViewInstanceSOP,
        injectedViewInstanceLTD,
        injectedViewInstanceUIB,
        injectedViewInstanceOBP,
        injectedViewInstanceLLP,
        injectedViewInstanceLP,
        injectedViewInstanceNRL,
        injectedViewInstanceDetailsNotFound,
        mockBusinessRegCacheConnector,
        mockBackLinkCache,
        mockBusinessMatchingService,
        businessRegUKController,
        busRegController,
        nrlQuestionController,
        reviewDetailsController,
        homeController,
        mcc
      ) {
        override val controllerId = "test"
      }
  }

  "BusinessVerificationController" must {

    "businessVerification" must {

      "authorised users" must {

        "respond with OK" in new Setup {
          when(
            mockBusinessRegCacheConnector
              .fetchAndGetCachedDetails[BusinessType](ArgumentMatchers.any())(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )
          )
            .thenReturn(Future.successful(None))
          businessVerificationWithAuthorisedUser(controller)(result =>
            status(result) must be(OK)
          )
        }

        "respond with OK for cached data" in new Setup {
          when(
            mockBusinessRegCacheConnector
              .fetchAndGetCachedDetails[BusinessType](ArgumentMatchers.any())(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )
          )
            .thenReturn(
              Future.successful(
                Some(
                  BusinessType(
                    Some("TestBusinessType"),
                    isSaAccount = false,
                    isOrgAccount = false
                  )
                )
              )
            )
          businessVerificationWithAuthorisedUser(controller)(result =>
            status(result) must be(OK)
          )
        }

        "respond with NotFound when invalid service is in uri" in new Setup {
          intercept[NotFoundException] {

            businessVerificationWithAuthorisedUser(controller)(
              result => status(result) must be(NOT_FOUND),
              serviceName = invalidService
            )
          }
        }

        "return Business Verification view for a user" in new Setup {

          when(
            mockBusinessRegCacheConnector
              .fetchAndGetCachedDetails[BusinessType](ArgumentMatchers.any())(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )
          )
            .thenReturn(Future.successful(None))
          businessVerificationWithAuthorisedUser(controller) { result =>
            val document = Jsoup.parse(contentAsString(result))

            document.title() must be("What is your business type? - Register as an alcohol wholesaler or producer - GOV.UK")
            document.getElementsByClass("govuk-caption-xl").text() must be(
              "This section is ATED registration"
            )
            document.getElementsByTag("h1").text() must include(
              "What is your business type?"
            )
            document.select(".govuk-radios__item").text() must include(
              "Limited company"
            )
            document.select(".govuk-radios__item").text() must include(
              "Limited liability partnership"
            )
            document.select(".govuk-radios__item").text() must include(
              "partnership"
            )
            document.select(".govuk-radios__item").text() must include(
              "I have an overseas company"
            )
            document.select(".govuk-radios__item").text() must include(
              "Unit trust or collective investment vehicle"
            )
            document.select(".govuk-radios__item").text() must include(
              "Limited partnership"
            )
            document.select("button").text() must be("Continue")
            document.getElementsByClass("govuk-back-link").text() must be(
              "Back"
            )
            document.getElementsByClass("govuk-back-link").attr("href") must be(
              "/ated-subscription/appoint-agent"
            )
          }
        }

        "redirect to 'haveYouRegisteredUrl' if service is 'awrs', and no backlink is found" in {
          val mockAppConfig = mock[ApplicationConfig]
          val userId = s"user-${UUID.randomUUID}"
          val service = "awrs"

          AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
          when(
            mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
          ).thenReturn(Future.successful(None))
          when(mockAppConfig.getNavTitle(service)).thenReturn(Some("bc.awrs.serviceName"))
          when(mockAppConfig.serviceList).thenReturn(List(service))
          when(mockAppConfig.haveYouRegisteredUrl).thenReturn("http://localhost:9913/alcohol-wholesale-scheme/have-you-registered")
          when(mockAppConfig.businessTypeMap(service, isAgent = false)).thenReturn(Seq(
            "OBP" -> "bc.business-verification.PRT", "GROUP" -> "bc.business-verification.GROUP", "LTD" -> "bc.business-verification.LTD",
            "LLP" -> "bc.business-verification.LLP", "LP" -> "bc.business-verification.LP",
            "SOP" -> "bc.business-verification.SOP", "UIB" -> "bc.business-verification.UIB"
          ))
          when(mockBusinessRegCacheConnector.fetchAndGetCachedDetails[BusinessType](ArgumentMatchers.any())(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(Future.successful(None))

          val businessVerificationController = new BusinessVerificationController(
            mockAppConfig,
            mockAuthConnector,
            injectedViewInstance,
            injectedViewInstanceSOP,
            injectedViewInstanceLTD,
            injectedViewInstanceUIB,
            injectedViewInstanceOBP,
            injectedViewInstanceLLP,
            injectedViewInstanceLP,
            injectedViewInstanceNRL,
            injectedViewInstanceDetailsNotFound,
            mockBusinessRegCacheConnector,
            mockBackLinkCache,
            mockBusinessMatchingService,
            businessRegUKController,
            busRegController,
            nrlQuestionController,
            reviewDetailsController,
            homeController,
            mcc
          ) {
            override val controllerId = "BusinessVerificationController"
          }

          val result = businessVerificationController.businessVerification(service)(FakeRequest())
          status(result) mustBe OK

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-back-link").attr("href") must be("http://localhost:9913/alcohol-wholesale-scheme/have-you-registered")
        }

        "return Business Verification view for an agent" in new Setup {

          businessVerificationWithAuthorisedAgent(controller) { result =>
            val document = Jsoup.parse(contentAsString(result))

            document.title() must be(
              "What is the business type for your agency? - Register as an alcohol wholesaler or producer - GOV.UK"
            )
            document.getElementsByClass("govuk-caption-xl").text() must be(
              "This section is ATED agency set up"
            )
            document.getElementsByTag("h1").text() must include(
              "What is the business type for your agency?"
            )
            document.select(".govuk-radios__item").text() must include(
              "Limited company"
            )
            document.select(".govuk-radios__item").text() must include(
              "Sole trader self-employed"
            )
            document.select(".govuk-radios__item").text() must include(
              "Limited liability partnership"
            )
            document.select(".govuk-radios__item").text() must include(
              "partnership"
            )
            document.select(".govuk-radios__item").text() must include(
              "I have an overseas company without a UK Unique Taxpayer Reference"
            )
            document.select(".govuk-radios__item").text() must include(
              "Limited partnership"
            )
            document.select(".govuk-radios__item").text() must include(
              "Unlimited company"
            )
            document.select("button").text() must be("Continue")
          }
        }
      }

      "unauthorised users" must {
        "respond with a redirect & be redirected to the unauthorised page" in new Setup {
          businessVerificationWithUnAuthorisedUser(controller) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result).get must include(
              "/business-customer/unauthorised"
            )
          }
        }

      }
    }

    "continue" must {

      "selecting continue with no business type selected must display error message" in new Setup {
        continueWithAuthorisedUserJson(controller, Map("businessType" -> "")) {
          result =>
            status(result) must be(BAD_REQUEST)
            contentAsString(result) must include("Select your type of business")
        }
      }

      "if non-uk with capital-gains-tax service, continue to registration page" in new Setup {
        when(
          mockBackLinkCache.saveBackLink(
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )(ArgumentMatchers.any(), ArgumentMatchers.any())
        ).thenReturn(Future.successful(None))
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "NUK"),
          "capital-gains-tax"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/register/capital-gains-tax/NUK"
          )
        }
      }

      "if non-uk, continue to registration page" in new Setup {
        willSaveBackLink("/business-customer/business-verification/ATED")
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "NUK")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/ated-subscription/previous"
          )
        }
      }

      "if non-uk Agent, continue to ATED NRL page" in new Setup {
        willSaveBackLink("/business-customer/business-verification/ATED")
        continueWithAuthorisedAgentJson(
          controller,
          "NUK",
          FakeRequest("POST", "/").withFormUrlEncodedBody(
            Map("businessType" -> "NUK").toSeq: _*
          )
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/nrl/ATED"
          )
        }
      }

      "if new, continue to NEW registration page" in new Setup {
        willSaveBackLink(s"/business-customer/business-verification/$service")
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "NEW")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/register-gb/$service/NEW"
          )
        }
      }

      "if group, continue to GROUP registration page" in new Setup {
        willSaveBackLink(s"/business-customer/business-verification/$service")
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "GROUP")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/register-gb/$service/GROUP"
          )
        }
      }

      "if OBP(OrdinaryBusinessPartnership), continue to GROUP registration page for AWRS" in new Setup {
        willSaveBackLink(s"/business-customer/business-verification/awrs")
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "OBP"),
          "awrs"
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            s"/business-customer/business-verification/awrs/businessForm/OBP"
          )
        }
      }

      "for any other option, redirect to home page again" in new Setup {
        when(
          mockBackLinkCache.saveBackLink(
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )(ArgumentMatchers.any(), ArgumentMatchers.any())
        ).thenReturn(Future.successful(None))
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "XYZ")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(
            Some(s"/business-customer/agent/$service")
          )
        }
      }
    }

    "when selecting Sole Trader option" must {

      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedSaUserJson(
          controller,
          Map(
            "businessType" -> "SOP",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "false"
          )
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "fail with a bad request when SOP is selected for an Org user" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map(
            "businessType" -> "SOP",
            "isSaAccount" -> "false",
            "isOrgAccount" -> "true"
          )
        ) { result =>
          status(result) must be(BAD_REQUEST)
          contentAsString(result) must include(
            "You are logged in as an organisation with your Government Gateway ID. You cannot select sole trader or self-employed as your business type. You need to have an individual Government Gateway ID and enrol for Self Assessment"
          )
        }
      }

      "redirect to next screen to allow additional form fields to be entered when user has both Sa and Org and selects SOP" in new Setup {
        continueWithAuthorisedSaOrgUserJson(
          controller,
          Map(
            "businessType" -> "SOP",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "true"
          )
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        businessLookupWithAuthorisedUser(controller, "SOP", "AWRS") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: AWRS registration"
          )
          document.select("h1").text() must include(
            "Your Self Assessment details"
          )
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "firstName")
            .text() must be("First name")
          document
            .getElementsByAttributeValue("for", "lastName")
            .text() must be("Last name")
          document.getElementsByAttributeValue("for", "saUTR").text() must be(
            "What is your Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("saUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/AWRS"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4)")
            .text() must include(
            "Your UTR can be 10 or 13 digits long. You can find it in your Personal Tax Account, the HMRC app or on tax returns and other documents from HMRC. It might be called ‘reference’, ‘UTR’ or ‘official use’."
          )
          document
            .select("#main-content > div > div > form > p:nth-child(5) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(5) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting Sole Trader" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "SOP") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.getElementsByTag("h1").text() must include(
            "Your agency details"
          )
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "firstName")
            .text() must be("First name")
          document
            .getElementsByAttributeValue("for", "lastName")
            .text() must be("Last name")
          document.getElementsByAttributeValue("for", "saUTR").text() must be(
            "What is your Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("saUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4)")
            .text() must include(
            "Your UTR can be 10 or 13 digits long. You can find it in your Personal Tax Account, the HMRC app or on tax returns and other documents from HMRC. It might be called ‘reference’, ‘UTR’ or ‘official use’."
          )
          document
            .select("#main-content > div > div > form > p:nth-child(5) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(5) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "return successful response for Sole Trader business type during AWRS journey without previous cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[SoleTraderMatch](ArgumentMatchers.any())(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessLookupWithAuthorisedAgent(controller, "SOP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Sole Trader business type during neither AWRS nor ATED journey" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "SOP", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Sole Trader business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[SoleTraderMatch](ArgumentMatchers.any())(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(
            Future.successful(
              Some(
                SoleTraderMatch("TestFirstName", "TestlastName", "TestSAUTR")
              )
            )
          )
        businessLookupWithAuthorisedAgent(controller, "SOP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Company business type during AWRS journey without previous cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[LimitedCompanyMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessLookupWithAuthorisedAgent(controller, "LTD", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Company business type during neither AWRS nor ATED journey" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "LTD", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Company business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[LimitedCompanyMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(
            Future.successful(
              Some(LimitedCompanyMatch("TestBusinessName", "TestCOTAXUTR"))
            )
          )
        businessLookupWithAuthorisedAgent(controller, "LTD", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Unincorporated business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[UnincorporatedMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(
            Future.successful(
              Some(UnincorporatedMatch("TestBusinessName", "TestCOTAXUTR"))
            )
          )
        businessLookupWithAuthorisedAgent(controller, "UIB", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Unincorporated business type during neither AWRS nor ATED journey" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "UIB", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Ordinary Business Partnership business type during AWRS journey without previous cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[OrdinaryBusinessPartnershipMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessLookupWithAuthorisedAgent(controller, "OBP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Ordinary Business Partnership type during neither AWRS nor ATED journey" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "OBP", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Ordinary Business Partnership business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[OrdinaryBusinessPartnershipMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(
            Future.successful(
              Some(
                OrdinaryBusinessPartnershipMatch(
                  "TestBusinessName",
                  "TestCOTAXUTR"
                )
              )
            )
          )
        businessLookupWithAuthorisedAgent(controller, "OBP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Liability Partnership business type during AWRS journey without previous cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[LimitedLiabilityPartnershipMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessLookupWithAuthorisedAgent(controller, "LLP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Liability Partnership business type during neither AWRS nor ATED journey" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "LLP", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Liability Partnership business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[LimitedLiabilityPartnershipMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(
            Future.successful(
              Some(
                LimitedLiabilityPartnershipMatch(
                  "TestBusinessName",
                  "TestPSAUTR"
                )
              )
            )
          )
        businessLookupWithAuthorisedAgent(controller, "LLP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Partnership business type during AWRS journey without previous cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[LimitedPartnershipMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessLookupWithAuthorisedAgent(controller, "LP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Partnership business type during neither AWRS nor ATED journey" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "LP", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Partnership business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[LimitedPartnershipMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(
            Future.successful(
              Some(LimitedPartnershipMatch("TestBusinessName", "TestPSAUTR"))
            )
          )
        businessLookupWithAuthorisedAgent(controller, "LP", "awrs") { result =>
          status(result) must be(OK)
        }
      }
    }

    "when selecting None Resident Landlord option" must {

      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "NRL")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "fail with a bad request when NRL is selected for an Sa user" in new Setup {
        continueWithAuthorisedSaUserJson(
          controller,
          Map(
            "businessType" -> "NRL",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "false"
          )
        ) { result =>
          status(result) must be(BAD_REQUEST)
          contentAsString(result) must include(
            "You are logged in as an individual with your Government Gateway ID. You cannot select limited company or partnership as your business type. You need to have an organisation Government Gateway ID."
          )
        }
      }

      "redirect to next screen to allow additional form fields to be entered when user has both Sa and Org and selects LTD" in new Setup {
        continueWithAuthorisedSaOrgUserJson(
          controller,
          Map(
            "businessType" -> "NRL",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "true"
          )
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "display the correct fields for a client" in new Setup {
        businessLookupWithAuthorisedUser(controller, "NRL", "ATED") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED registration"
          )
          document.select("h1").text() must include(
            "Your Self Assessment details"
          )
          document.getElementById("business-type-paragraph").text() must be(
            "Enter your Self Assessment details and we will attempt to match them against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document.getElementsByAttributeValue("for", "saUTR").text() must be(
            "What is your Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("saUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/register/non-uk-client/paySA/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(3)")
            .text() must include(
            "Your UTR can be 10 or 13 digits long. You can find it in your Personal Tax Account, the HMRC app or on tax returns and other documents from HMRC. It might be called ‘reference’, ‘UTR’ or ‘official use’."
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting None Resident Landlord" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "NRL") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.select("h1").text() must include("Your agency details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must include("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "saUTR")
            .text() must include(
            "Self Assessment Unique Taxpayer Reference (UTR)"
          )
          document.getElementById("saUTR").attr("type") must be("text")
          document.getElementById("submit").text() must include("Continue")

        }
      }
    }

    "when selecting Limited Company option" must {

      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "LTD")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "fail with a bad request when LTD is selected for an Sa user" in new Setup {
        continueWithAuthorisedSaUserJson(
          controller,
          Map(
            "businessType" -> "LTD",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "false"
          )
        ) { result =>
          status(result) must be(BAD_REQUEST)
          contentAsString(result) must include(
            "You are logged in as an individual with your Government Gateway ID. You cannot select limited company or partnership as your business type. You need to have an organisation Government Gateway ID."
          )
        }
      }

      "redirect to next screen to allow additional form fields to be entered when user has both Sa and Org and selects LTD" in new Setup {
        continueWithAuthorisedSaOrgUserJson(
          controller,
          Map(
            "businessType" -> "LTD",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "true"
          )
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        when(mockBusinessRegCacheConnector.fetchAndGetCachedDetails[LimitedCompanyMatch](ArgumentMatchers.any())
          (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(LimitedCompanyMatch("TestBusinessName", "TestCOTAXUTR"))))
        businessLookupWithAuthorisedUser(controller, "LTD") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED registration"
          )
          document.select("h1").text() must include("Your business details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "cotaxUTR")
            .text() must be(
            "What is your Corporation Tax Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("cotaxUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(1)")
            .text() must include(
            "UTRs are 10 digits long, for example, ‘1234567890’. Some UTRs may appear as 13 digits, for example, ‘1234567890123’. If so, enter only the last 10 digits."
          )
          document.select("#cotaxUTR-hint > p:nth-child(2) > a").text() must be(
            "Find your UTR number (opens in new tab)"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(2) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting Limited Company" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "LTD") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.select("h1").text() must include("Your agency details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "cotaxUTR")
            .text() must be(
            "What is your Corporation Tax Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("cotaxUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(1)")
            .text() must include(
            "UTRs are 10 digits long, for example, ‘1234567890’. Some UTRs may appear as 13 digits, for example, ‘1234567890123’. If so, enter only the last 10 digits."
          )
          document.select("#cotaxUTR-hint > p:nth-child(2) > a").text() must be(
            "Find your UTR number (opens in new tab)"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(2) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }
    }

    "when selecting Unit Trust option" must {

      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "UT")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "fail with a bad request when UT is selected for an Sa user" in new Setup {
        continueWithAuthorisedSaUserJson(
          controller,
          Map(
            "businessType" -> "UT",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "false"
          )
        ) { result =>
          status(result) must be(BAD_REQUEST)
          contentAsString(result) must include(
            "You are logged in as an individual with your Government Gateway ID. You cannot select limited company or partnership as your business type. You need to have an organisation Government Gateway ID."
          )
        }
      }

      "redirect to next screen to allow additional form fields to be entered when user has both Sa and Org and selects UT" in new Setup {
        continueWithAuthorisedSaOrgUserJson(
          controller,
          Map(
            "businessType" -> "UT",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "true"
          )
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        businessLookupWithAuthorisedUser(controller, "UT") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED registration"
          )
          document.select("h1").text() must include("Your business details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "cotaxUTR")
            .text() must be(
            "What is your Corporation Tax Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("cotaxUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(1)")
            .text() must include(
            "UTRs are 10 digits long, for example, ‘1234567890’. Some UTRs may appear as 13 digits, for example, ‘1234567890123’. If so, enter only the last 10 digits."
          )
          document.select("#cotaxUTR-hint > p:nth-child(2) > a").text() must be(
            "Find your UTR number (opens in new tab)"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(2) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }
    }

    "when selecting Unincorporated Body option" must {

      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "UIB")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[UnincorporatedMatch](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessLookupWithAuthorisedUser(controller, "UIB", "AWRS") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: AWRS registration"
          )
          document.select("h1").text() must include("Your business details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "cotaxUTR")
            .text() must be(
            "What is your Corporation Tax Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("cotaxUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/AWRS"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(1)")
            .text() must include(
            "UTRs are 10 digits long, for example, ‘1234567890’. Some UTRs may appear as 13 digits, for example, ‘1234567890123’. If so, enter only the last 10 digits."
          )
          document.select("#cotaxUTR-hint > p:nth-child(2) > a").text() must be(
            "Find your UTR number (opens in new tab)"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(2) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting Unincorporated Association option" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "UIB") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.select("h1").text() must include("Your agency details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "cotaxUTR")
            .text() must be(
            "What is your Corporation Tax Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("cotaxUTR").attr("type") must be("text")
          document.getElementById("submit").text() must include("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(1)")
            .text() must include(
            "UTRs are 10 digits long, for example, ‘1234567890’. Some UTRs may appear as 13 digits, for example, ‘1234567890123’. If so, enter only the last 10 digits."
          )
          document.select("#cotaxUTR-hint > p:nth-child(2) > a").text() must be(
            "Find your UTR number (opens in new tab)"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(2) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }
    }

    "when selecting Ordinary business partnership" must {
      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "OBP")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        businessLookupWithAuthorisedUser(controller, "OBP") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED registration"
          )
          document.select("h1").text() must include("Your business details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your partnership name?")
          document.select("#businessName-hint").text() must be(
            "This is the name that you registered with HMRC"
          )
          document.getElementsByAttributeValue("for", "psaUTR").text() must be(
            "What is your Partnership Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("psaUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(3)")
            .text() must include(
            "Your UTR can be 10 or 13 digits long. You can find it in your Personal Tax Account, the HMRC app or on tax returns and other documents from HMRC. It might be called ‘reference’, ‘UTR’ or ‘official use’."
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting Ordinary Business Partnership option" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "OBP") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.select("h1").text() must include("Your agency details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your partnership name?")
          document.select("#businessName-hint").text() must be(
            "This is the name that you registered with HMRC"
          )
          document.getElementsByAttributeValue("for", "psaUTR").text() must be(
            "What is your Partnership Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("psaUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(3)")
            .text() must include(
            "Your UTR can be 10 or 13 digits long. You can find it in your Personal Tax Account, the HMRC app or on tax returns and other documents from HMRC. It might be called ‘reference’, ‘UTR’ or ‘official use’."
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }
    }

    "when selecting Limited Liability Partnership option" must {
      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "LLP")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        businessLookupWithAuthorisedUser(controller, "LLP") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED registration"
          )
          document
            .select("h1")
            .text() contains "What are your business details?"
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document.getElementsByAttributeValue("for", "psaUTR").text() must be(
            "What is your Partnership Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("psaUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(3)")
            .text() must include(
            "Your UTR can be 10 or 13 digits long. You can find it in your Personal Tax Account, the HMRC app or on tax returns and other documents from HMRC. It might be called ‘reference’, ‘UTR’ or ‘official use’."
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting Limited Liability Partnership option" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "LLP") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.select("h1").text() must include("Your agency details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document.getElementsByAttributeValue("for", "psaUTR").text() must be(
            "What is your Partnership Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("psaUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(3)")
            .text() must include(
            "Your UTR can be 10 or 13 digits long. You can find it in your Personal Tax Account, the HMRC app or on tax returns and other documents from HMRC. It might be called ‘reference’, ‘UTR’ or ‘official use’."
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }
    }

    "when selecting Limited Partnership option" must {
      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "LP")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        businessLookupWithAuthorisedUser(controller, "LP") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED registration"
          )
          document.select("h1").text() must include("Your business details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your partnership name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document.getElementsByAttributeValue("for", "psaUTR").text() must be(
            "What is your Partnership Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("psaUTR").attr("type") must be("text")
          document.getElementById("submit").text() must include("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4)")
            .text() must include("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting Limited Partnership option" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "LP") { result =>
          status(result) must be(OK)
          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.select("h1").text() must include("Your agency details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your partnership name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document.getElementsByAttributeValue("for", "psaUTR").text() must be(
            "What is your Partnership Self Assessment Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("psaUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#main-content > div > div > form > p:nth-child(4)")
            .text() must include("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .text() must be("How to find your UTR (opens in new tab)")
          document
            .select("#main-content > div > div > form > p:nth-child(4) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }
    }

    "when selecting Unlimited Company option" must {

      "redirect to next screen to allow additional form fields to be entered" in new Setup {
        continueWithAuthorisedUserJson(
          controller,
          Map("businessType" -> "ULTD")
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "fail with a bad request when ULTD is selected for an Sa user" in new Setup {
        continueWithAuthorisedSaUserJson(
          controller,
          Map(
            "businessType" -> "ULTD",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "false"
          )
        ) { result =>
          status(result) must be(BAD_REQUEST)
          contentAsString(result) must include(
            "You are logged in as an individual with your Government Gateway ID. You cannot select limited company or partnership as your business type. You need to have an organisation Government Gateway ID."
          )
        }
      }

      "redirect to next screen to allow additional form fields to be entered when user has both Sa and Org and selects ULTD" in new Setup {
        continueWithAuthorisedSaOrgUserJson(
          controller,
          Map(
            "businessType" -> "ULTD",
            "isSaAccount" -> "true",
            "isOrgAccount" -> "true"
          )
        ) { result =>
          status(result) must be(SEE_OTHER)
          redirectLocation(result).get must include(
            "/business-verification/ATED/businessForm"
          )
        }
      }

      "add additional form fields to the screen for entry" in new Setup {
        businessLookupWithAuthorisedUser(controller, "ULTD") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED registration"
          )
          document
            .select("h1")
            .text() contains "What are your business details?"
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "cotaxUTR")
            .text() must be(
            "What is your Corporation Tax Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("cotaxUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(1)")
            .text() must include(
            "UTRs are 10 digits long, for example, ‘1234567890’. Some UTRs may appear as 13 digits, for example, ‘1234567890123’. If so, enter only the last 10 digits."
          )
          document.select("#cotaxUTR-hint > p:nth-child(2) > a").text() must be(
            "Find your UTR number (opens in new tab)"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(2) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }

      "display correct heading for AGENT selecting Unlimited Company" in new Setup {
        businessLookupWithAuthorisedAgent(controller, "ULTD") { result =>
          status(result) must be(OK)

          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-caption-xl").text() must be(
            "This section is: ATED agency set up"
          )
          document.select("h1").text() must include("Your agency details")
          document.getElementById("business-type-paragraph").text() must be(
            "We will attempt to match your details against information we currently hold."
          )
          document
            .getElementsByAttributeValue("for", "businessName")
            .text() must be("What is your registered company name?")
          document.select("#businessName-hint").text() must be(
            "This is the registered name on your incorporation certificate."
          )
          document
            .getElementsByAttributeValue("for", "cotaxUTR")
            .text() must be(
            "What is your Corporation Tax Unique Taxpayer Reference (UTR)?"
          )
          document.getElementById("cotaxUTR").attr("type") must be("text")
          document.getElementById("submit").text() must be("Continue")
          document.getElementsByClass("govuk-back-link").text() must be("Back")
          document.getElementsByClass("govuk-back-link").attr("href") must be(
            "/business-customer/business-verification/ATED"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(1)")
            .text() must include(
            "UTRs are 10 digits long, for example, ‘1234567890’. Some UTRs may appear as 13 digits, for example, ‘1234567890123’. If so, enter only the last 10 digits."
          )
          document.select("#cotaxUTR-hint > p:nth-child(2) > a").text() must be(
            "Find your UTR number (opens in new tab)"
          )
          document
            .select("#cotaxUTR-hint > p:nth-child(2) > a")
            .attr("href") must be("https://www.gov.uk/find-utr-number")
        }
      }
    }
  }

  "detailsNotFound" should {
    "display the details not found page" when {
      "called" in new Setup {
        AuthBuilder.mockAuthorisedUser("test", mockAuthConnector)

        status(
          controller
            .detailsNotFound("ated", "businessType")
            .apply(
              FakeRequest()
                .withSession(
                  "sessionId" -> "test",
                  "token" -> "RANDOMTOKEN",
                  "userId" -> "userId"
                )
                .withHeaders(Headers("Authorization" -> "value"))
            )
        ) mustBe OK
      }
    }
  }

  private def willSaveBackLink(backLink: String) =
    when(
      mockBackLinkCache.saveBackLink(
        ArgumentMatchers.eq(null),
        ArgumentMatchers.eq(Some(backLink))
      )(ArgumentMatchers.any(), ArgumentMatchers.any())
    )
      .thenReturn(Future.successful(None))

  def businessVerificationWithAuthorisedUser(
      controller: BusinessVerificationController
  )(test: Future[Result] => Any, serviceName: String = service): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(Some("/ated-subscription/appoint-agent")))

    val result = controller
      .businessVerification(serviceName)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token" -> "RANDOMTOKEN",
            "userId" -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )

    test(result)
  }

  def businessVerificationWithAuthorisedAgent(
      controller: BusinessVerificationController
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .businessVerification(service)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token" -> "RANDOMTOKEN",
            "userId" -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )

    test(result)
  }

  def businessLookupWithAuthorisedUser(
      controller: BusinessVerificationController,
      businessType: String,
      serviceName: String = service
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .businessForm(serviceName, businessType)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token" -> "RANDOMTOKEN",
            "userId" -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )

    test(result)
  }

  def businessVerificationWithUnAuthorisedUser(
      controller: BusinessVerificationController
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .businessVerification(service)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token" -> "RANDOMTOKEN",
            "userId" -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )

    test(result)
  }

  def continueWithAuthorisedUserJson(
      controller: BusinessVerificationController,
      fields: Map[String, String],
      service: String = service
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"
    def generateRequest: FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest("POST", "/")
        .withSession(
          "sessionId" -> sessionId,
          "token" -> "RANDOMTOKEN",
          "userId" -> userId
        )
        .withHeaders(Headers("Authorization" -> "value"))
        .withFormUrlEncodedBody(fields.toSeq: _*)
    }
    AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller.continue(service).apply(generateRequest)

    test(result)
  }

  def continueWithAuthorisedAgentJson(
      controller: BusinessVerificationController,
      businessType: String,
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
      service: String = service
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .continue(service)
      .apply(
        fakeRequest
          .withSession(
            "sessionId" -> sessionId,
            "token" -> "RANDOMTOKEN",
            "userId" -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )

    test(result)
  }

  def continueWithAuthorisedSaUserJson(
      controller: BusinessVerificationController,
      fields: Map[String, String]
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"
    def generateRequest: FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest("POST", "/")
        .withSession(
          "sessionId" -> sessionId,
          "token" -> "RANDOMTOKEN",
          "userId" -> userId
        )
        .withHeaders(Headers("Authorization" -> "value"))
        .withFormUrlEncodedBody(fields.toSeq: _*)
    }
    AuthBuilder.mockAuthorisedSaUser(userId, mockAuthConnector)

    val result = controller.continue(service).apply(generateRequest)

    test(result)
  }

  def continueWithAuthorisedSaOrgUserJson(
      controller: BusinessVerificationController,
      fields: Map[String, String]
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"
    def generateRequest: FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest("POST", "/")
        .withSession(
          "sessionId" -> sessionId,
          "token" -> "RANDOMTOKEN",
          "userId" -> userId
        )
        .withHeaders(Headers("Authorization" -> "value"))
        .withFormUrlEncodedBody(fields.toSeq: _*)
    }

    AuthBuilder.mockAuthorisedSaOrgUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller.continue(service).apply(generateRequest)

    test(result)
  }

  def continueWithAuthorisedUser(
      controller: BusinessVerificationController,
      businessType: String,
      fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded]
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .continue(service)
      .apply(
        fakeRequest
          .withSession(
            "sessionId" -> sessionId,
            "token" -> "RANDOMTOKEN",
            "userId" -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
          .withMethod("POST")
      )

    test(result)
  }

  def businessLookupWithAuthorisedAgent(
      controller: BusinessVerificationController,
      businessType: String,
      service: String = service
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .businessForm(service, businessType)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token" -> "RANDOMTOKEN",
            "userId" -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )

    test(result)
  }

}
