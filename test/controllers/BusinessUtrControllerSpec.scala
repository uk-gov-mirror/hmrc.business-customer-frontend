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
import forms._
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.Inspectors.forAll
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

class BusinessUtrControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with Injecting {

  val request: FakeRequest[_]                              = FakeRequest()
  val mockBusinessMatchingService: BusinessMatchingService =
    mock[BusinessMatchingService]
  val mockAuthConnector: AuthConnector                         = mock[AuthConnector]
  val mockBackLinkCache: BackLinkCacheConnector                = mock[BackLinkCacheConnector]
  val mockBusinessRegCacheConnector: BusinessRegCacheConnector =
    mock[BusinessRegCacheConnector]

  val mockReviewDetailsController: ReviewDetailsController =
    mock[ReviewDetailsController]
  val injectedViewInstanceBusinessUtr: generic_business_utr =
    inject[views.html.generic_business_utr]

  val mockBusinessVerificationController: BusinessVerificationController = mock[BusinessVerificationController]

  val appConfig: ApplicationConfig               = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents =
    inject[MessagesControllerComponents]
  implicit val messages: Messages =
    mcc.messagesApi.preferred(Seq(Lang.defaultLang))

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

  private val messageFinder = Map(
    "alreadyRegistered"       -> "bc.business-utr.paragraph.registered",
    "findCorporationTax"      -> "bc.business-utr.paragraph.find.corporation",
    "findPartnershipTax"      -> "bc.business-utr.paragraph.find.partnership",
    "findSATax"               -> "bc.business-utr.paragraph.find.soleTrader",
    "getHelpLinkTextCorporation" -> "bc.business-verification.utr.corporationUTRLinkText",
    "getHelpLinkText"         -> "bc.business-verification.utr.UTRLinkText",
    "getHelpLinkTextAmls"     -> "bc.business-verification.utr.amls.UTRLinkText",
    "UtrLengthHint"           -> "bc.business-verification.utr.lengthHint",
    "selfAssessmentPageTitle" -> "bc.business-verification.saUTR",
    "companyPageTitle"        -> "bc.business-verification.coUTR",
    "partnershipPageTitle"    -> "bc.business-verification.psaUTR",
    "almsParagraphText"       -> "bc.business-utr.paragraph.amls.corporation",
    "questionText"            -> "bc.business-verification.genericUTR.short"
  )

  private def getMessage(
                          key: String,
                          service: Option[String] = None,
                          subString: Option[String] = None
                        ): String = {
    if (service.isDefined) {
      messages(
        messageFinder(key),
        messages(s"bc.business-utr.paragraph.registered.${service.get.toLowerCase}")
      )
    } else if (key == "questionText") {
      subString match {
        case Some(value) => messages(messageFinder(key), value)
        case None        => messages(messageFinder(key))
      }
    } else {
      messages(messageFinder(key))
    }
  }

  val businessTypes: Seq[String] =
    List("SOP", "LTD", "UT", "ULTD", "UIB", "OBP", "LP", "LLP")
  val services: Seq[String] = List("ATED", "AWRS", "AMLS", "FHDDS")

  val getBusinessTypesForService: Map[String, Seq[String]] = Map(
    "ATED"  -> Seq("LTD", "UT", "ULTD", "OBP", "LP", "LLP"),
    "AWRS"  -> Seq("SOP", "OBP", "LTD", "LLP", "LP", "UIB"),
    "AMLS"  -> Seq("SOP", "LTD", "UIB", "OBP", "LLP"),
    "FHDDS" -> Seq("SOP", "LTD", "UIB", "OBP", "LLP", "LP")
  )
  val getServiceMessageKey: Map[String, String] = Map(
    "ATED"  -> messages("bc.ated.serviceName"),
    "AWRS"  -> messages("bc.awrs.serviceName"),
    "AMLS"  -> messages("bc.amls.serviceName"),
    "FHDDS" -> messages("bc.fhdds.serviceName")
  )

  val corporations: Seq[String] = Seq("LTD", "UT", "ULTD", "UIB")
  val partnerships: Seq[String] = Seq("OBP", "LP", "LLP")

  "BusinessUtrController" must {

    "onPageLoad" must {

      "authorised users" must {

        "respond with OK" in new Setup {
          forAll(services) { (service) =>
            forAll(businessTypes) { (businessType) =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType) && corporations.contains(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessUtrWithAuthorisedUser(controller, businessType, service)(result => status(result) must be(OK))
              }
            }
          }
        }
        "respond with NotFound when invalid service is in uri" in new Setup {
          val serviceName = "INVALID_SERVICE"
          forAll(businessTypes) { (businessType) =>
            intercept[NotFoundException] {

              businessUtrWithAuthorisedUser(controller, businessType, serviceName)(result => status(result) must be(NOT_FOUND))
            }
          }
        }

        "return Corporation Tax UTR Form view for a user" in new Setup {
          forAll(services.filterNot(_.equals("AMLS"))) { (service) =>
            forAll(businessTypes) { (businessType) =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType) && corporations.contains(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
                  val document = Jsoup.parse(contentAsString(result))

                  document.title() must be(
                    s"${getMessage("companyPageTitle")} - ${getServiceMessageKey
                        .getOrElse(service, "")} - GOV.UK"
                  )
                  document
                    .getElementsByClass("govuk-caption-xl")
                    .text() must be(
                    s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
                  )
                  document.getElementById("business-find-paragraph").text() must include(
                    getMessage("findCorporationTax", Some(service))
                  )
                  document.getElementById("business-type-paragraph").text() must include(
                    getMessage("alreadyRegistered", Some(service))
                  )
                  document.getElementsByClass("govuk-link").text() must include(
                    getMessage("getHelpLinkTextCorporation")
                  )
                  document.getElementsByClass("govuk-link").text() must include(
                    "Is this page not working properly? (opens in new tab)"
                  )
                  document.select("label.govuk-label").eachText().get(0) must be(
                    getMessage("questionText", subString = Some("Corporation"))
                  )
                  document.getElementsByClass("govuk-hint").text() must include(
                    getMessage("UtrLengthHint")
                  )
                }
              }
            }
          }
        }
        "return Corporation Tax UTR Form view in AMLS for a user" in new Setup {
            forAll(businessTypes) { (businessType) =>
              val service = "AMLS"
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType) && corporations.contains(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
                  val document = Jsoup.parse(contentAsString(result))

                  document.title() must be(
                    s"${getMessage("companyPageTitle")} - ${getServiceMessageKey
                        .getOrElse(service, "")} - GOV.UK"
                  )
                  document
                    .getElementsByClass("govuk-caption-xl")
                    .text() must be(
                    s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
                  )
                  document.getElementById("business-find-paragraph").text() must include(
                    getMessage("findCorporationTax", Some(service))
                  )
                  document.getElementById("business-type-paragraph").text() must include(
                    getMessage("alreadyRegistered", Some(service))
                  )
                  document.getElementsByClass("govuk-link").text() must include(
                    getMessage("getHelpLinkTextAmls")
                  )
                  document.getElementsByClass("govuk-link").text() must include(
                    "Is this page not working properly? (opens in new tab)"
                  )
                  document.select("label.govuk-label").eachText().get(0) must be(
                    getMessage("questionText", subString = Some("Corporation"))
                  )
                  document.getElementsByClass("govuk-hint").text() must include(
                    getMessage("UtrLengthHint")
                  )
                }
              }
            }
          }

        "return Corporation Tax UTR Form view for a user when cached utr is retrieved" in new Setup {
          forAll(services) { (service) =>
            forAll(businessTypes) { (businessType) =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType) && corporations.contains(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(Some(Utr("1111111111"))))
                businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
                  val document = Jsoup.parse(contentAsString(result))

                  document.title() must be(
                    s"${getMessage("companyPageTitle")} - ${getServiceMessageKey
                        .getOrElse(service, "")} - GOV.UK"
                  )
                  document.getElementById("utr").attr("value") must be("1111111111")
                }
              }
            }
          }
        }
      }
      "return Partnership Tax UTR Form view for a user" in new Setup {
        forAll(services.filterNot(_.equals("AMLS"))) { (service) =>
          forAll(businessTypes) { (businessType) =>
            if (
              getBusinessTypesForService(service).exists(
                _.equalsIgnoreCase(businessType) && partnerships.contains(businessType)
              )
            ) {
              when(
                mockBusinessRegCacheConnector
                  .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                  )
              )
                .thenReturn(Future.successful(None))
              businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
                val document = Jsoup.parse(contentAsString(result))

                document.title() must be(
                  s"${getMessage("partnershipPageTitle")} - ${getServiceMessageKey
                      .getOrElse(service, "")} - GOV.UK"
                )
                document
                  .getElementsByClass("govuk-caption-xl")
                  .text() must be(
                  s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
                )
                document.getElementById("business-find-paragraph").text() must include(
                  getMessage("findPartnershipTax", Some(service))
                )
                document.getElementById("business-type-paragraph").text() must include(
                  getMessage("alreadyRegistered", Some(service))
                )
                document.getElementsByClass("govuk-link").text() must include(
                  getMessage("getHelpLinkText")
                )
                document.getElementsByClass("govuk-link").text() must include(
                  "Is this page not working properly? (opens in new tab)"
                )
                document.select("label.govuk-label").eachText().get(0) must be(
                  getMessage("questionText", subString = Some("Partnership"))
                )
                document.getElementsByClass("govuk-hint").text() must include(
                  getMessage("UtrLengthHint")
                )
              }
            }
          }
        }
      }
      "return Partnership Tax UTR Form view for a user when cached utr is retrieved" in new Setup {
        forAll(services) { (service) =>
          forAll(businessTypes) { (businessType) =>
            if (
              getBusinessTypesForService(service).exists(
                _.equalsIgnoreCase(businessType) && partnerships.contains(businessType)
              )
            ) {
              when(
                mockBusinessRegCacheConnector
                  .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                  )
              )
                .thenReturn(Future.successful(Some(Utr("1111111111"))))
              businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
                val document = Jsoup.parse(contentAsString(result))

                document.title() must be(
                  s"${getMessage("partnershipPageTitle")} - ${getServiceMessageKey
                    .getOrElse(service, "")} - GOV.UK"
                )
                document.getElementById("utr").attr("value") must be("1111111111")
              }
            }
          }
        }
      }

      "return Self Assessment Tax UTR Form view for a user" in new Setup {
        forAll(services.filterNot(_.equals("AMLS"))) { (service) =>
          val businessType = "SOP"
          if (
            getBusinessTypesForService(service).exists(
              _.equalsIgnoreCase(businessType)
            )
          ) {
            when(
              mockBusinessRegCacheConnector
                .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                  ArgumentMatchers.any(),
                  ArgumentMatchers.any(),
                  ArgumentMatchers.any()
                )
            )
              .thenReturn(Future.successful(None))
            businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
              val document = Jsoup.parse(contentAsString(result))

              document.title() must be(
                s"${getMessage("selfAssessmentPageTitle")} - ${getServiceMessageKey
                    .getOrElse(service, "")} - GOV.UK"
              )
              document
                .getElementsByClass("govuk-caption-xl")
                .text() must be(
                s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
              )
              document.getElementById("business-find-paragraph").text() must include(
                getMessage("findSATax", Some(service))
              )
              document.getElementById("business-type-paragraph").text() must include(
                getMessage("alreadyRegistered", Some(service))
              )
              document.getElementsByClass("govuk-link").text() must include(
                getMessage("getHelpLinkText")
              )
              document.getElementsByClass("govuk-link").text() must include(
                "Is this page not working properly? (opens in new tab)"
              )
              document.select("label.govuk-label").eachText().get(0) must be(
                getMessage("questionText", subString = Some("Self Assessment"))
              )
              document.getElementsByClass("govuk-hint").text() must include(
                getMessage("UtrLengthHint")
              )
            }
          }
        }
      }
      "return Self Assessment Tax UTR Form view in AMLS for a user" in new Setup {
        val service = "AMLS"
        val businessType = "SOP"
        if (
          getBusinessTypesForService(service).exists(
            _.equalsIgnoreCase(businessType)
          )
        ) {
          when(
            mockBusinessRegCacheConnector
              .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )
          )
            .thenReturn(Future.successful(None))
          businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title() must be(
              s"${getMessage("selfAssessmentPageTitle")} - ${getServiceMessageKey
                .getOrElse(service, "")} - GOV.UK"
            )
            document
              .getElementsByClass("govuk-caption-xl")
              .text() must be(
              s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
            )
            document.getElementById("business-find-paragraph").text() must include(
              getMessage("findSATax", Some(service))
            )
            document.getElementById("business-type-paragraph").text() must include(
              getMessage("alreadyRegistered", Some(service))
            )
            document.getElementsByClass("govuk-link").text() must include(
              getMessage("getHelpLinkTextAmls")
            )
            document.getElementsByClass("govuk-link").text() must include(
              "Is this page not working properly? (opens in new tab)"
            )
            document.select("label.govuk-label").eachText().get(0) must be(
              getMessage("questionText", subString = Some("Self Assessment"))
            )
            document.getElementsByClass("govuk-hint").text() must include(
              getMessage("UtrLengthHint")
            )
          }
        }
      }
      "return SA Tax UTR Form view for a user when cached utr data is retrieved" in new Setup {
        forAll(services) { (service) =>
          val businessType = "SOP"
          when(
            mockBusinessRegCacheConnector
              .fetchAndGetCachedDetails[Utr](ArgumentMatchers.any())(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )
          )
            .thenReturn(Future.successful(Some(Utr("1111111111"))))
          businessUtrWithAuthorisedUser(controller, businessType, service) { result =>
            val document = Jsoup.parse(contentAsString(result))

            document.title() must be(
              s"${getMessage("selfAssessmentPageTitle")} - ${getServiceMessageKey
                  .getOrElse(service, "")} - GOV.UK"
            )
            document.getElementById("utr").attr("value") must be("1111111111")
          }
        }
      }

      "unauthorised users" must {
        val businessType = "LTD"
        val serviceName  = "ATED"
        "respond with a redirect & be redirected to the unauthorised page" in new Setup {
          businessLookupWithUnAuthorisedUser(controller, businessType, serviceName) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result).get must include(
              "/business-customer/unauthorised"
            )
          }
        }
      }
    "display correct heading for AGENT selecting Corporation options" in new Setup {
      forAll(services.filterNot(_.equals("AMLS"))) { (service) =>
        forAll(businessTypes) { (businessType) =>
          if (
            getBusinessTypesForService(service).exists(
              _.equalsIgnoreCase(businessType) && corporations.contains(businessType)
            )
          ) {
            when(mockBusinessRegCacheConnector.fetchAndGetCachedDetails[Utr](
              ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
              .thenReturn(Future.successful(None))
            businessLookupWithAuthorisedAgent(controller, businessType, service) { result =>
              status(result) must be(OK)

              val document = Jsoup.parse(contentAsString(result))
              document.getElementsByClass("govuk-caption-xl").text() must be(
                s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.agent.text", service.toUpperCase)}"
              )
              document.select("h1").text() must include(messages("bc.business-verification-utr-selected-agent-header"))
              document.getElementById("business-type-paragraph").text()
              if (service == "AMLS") {
                include(
                  getMessage("almsParagraphText")
                )
              } else {
                getMessage("findCorporationTax")
              }
              document.select("label.govuk-label").eachText().get(0) must be(
                getMessage("questionText", subString = Some("Corporation"))
              )
              document.select("label.govuk-label").eachText().get(0) must be(
                getMessage("questionText", subString = Some("Corporation"))
              )
              document.getElementsByClass("govuk-link").text()
              if (service == "AMLS") {
                include(
                  getMessage("getHelpLinkTextAmls")
                )
              } else {
                getMessage("getHelpLinkTextCorporation")
              }
              document.getElementsByClass("govuk-link").text() must include(
                "Is this page not working properly? (opens in new tab)"
              )
              document.select("label.govuk-label").eachText().get(0) must be(
                getMessage("questionText", subString = Some("Corporation"))
              )
              document.getElementsByClass("govuk-hint").text() must include(
                getMessage("UtrLengthHint")
              )
            }
          }
        }
      }
    }
  }}
  private def businessUtrWithAuthorisedUser(
    controller: BusinessUtrController,
    businessType: String,
    serviceName: String
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId    = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .onPageLoad(serviceName, businessType)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token"     -> "RANDOMTOKEN",
            "userId"    -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )

    test(result)
  }

  def businessLookupWithUnAuthorisedUser(
    controller: BusinessUtrController,
    businessType: String,
    serviceName: String
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId    = s"user-${UUID.randomUUID}"

    AuthBuilder.mockUnAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .onPageLoad(serviceName, businessType)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token"     -> "RANDOMTOKEN",
            "userId"    -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )
    test(result)
  }

  def businessLookupWithAuthorisedAgent(
    controller: BusinessUtrController,
    businessType: String,
    service: String
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId    = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedAgent(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller
      .onPageLoad(service, businessType)
      .apply(
        FakeRequest()
          .withSession(
            "sessionId" -> sessionId,
            "token"     -> "RANDOMTOKEN",
            "userId"    -> userId
          )
          .withHeaders(Headers("Authorization" -> "value"))
      )
    test(result)
  }
}
