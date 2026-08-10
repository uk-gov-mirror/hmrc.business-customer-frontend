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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.NotFoundException
import views.html._

import java.util.UUID
import scala.concurrent.Future

class BusinessNameControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with Injecting with BusinessMatchTestObjects {

  val request: FakeRequest[AnyContentAsEmpty.type]             = FakeRequest()
  val mockAuthConnector: AuthConnector                         = mock[AuthConnector]
  val mockBackLinkCache: BackLinkCacheConnector                = mock[BackLinkCacheConnector]
  val mockBusinessRegCacheConnector: BusinessRegCacheConnector =
    mock[BusinessRegCacheConnector]

  val appConfig: ApplicationConfig               = inject[ApplicationConfig]
  implicit val mcc: MessagesControllerComponents =
    inject[MessagesControllerComponents]
  implicit val messages: Messages =
    mcc.messagesApi.preferred(Seq(Lang.defaultLang))
  val injectedViewInstanceGenericName: generic_business_name =
    inject[views.html.generic_business_name]

  val mockBusinessUtrController: BusinessUtrController = mock[BusinessUtrController]

  class Setup {
    val controller: BusinessNameController =
      new BusinessNameController(
        appConfig,
        mockAuthConnector,
        injectedViewInstanceGenericName,
        mockBusinessRegCacheConnector,
        mockBackLinkCache,
        mockBusinessUtrController,
        mcc
      ) {
        override val controllerId = "test"
      }
  }

  val getServiceMessageKey: Map[String, String] = Map(
    "ATED"  -> messages("bc.ated.serviceName"),
    "AWRS"  -> messages("bc.awrs.serviceName"),
    "AMLS"  -> messages("bc.amls.serviceName"),
    "FHDDS" -> messages("bc.fhdds.serviceName")
  )
  val businessTypes: Seq[String] =
    List("SOP", "LTD", "UT", "ULTD", "UIB", "OBP", "LP", "LLP")
  val services: Seq[String] = List("ATED", "AWRS", "AMLS", "FHDDS")

  val getBusinessTypesForService: Map[String, Seq[String]] = Map(
    "ATED"  -> Seq("LTD", "UT", "ULTD", "OBP", "LP", "LLP"),
    "AWRS"  -> Seq("SOP", "OBP", "LTD", "LLP", "LP", "UIB"),
    "AMLS"  -> Seq("SOP", "LTD", "UIB", "OBP", "LLP"),
    "FHDDS" -> Seq("SOP", "LTD", "UIB", "OBP", "LLP", "LP")
  )

  "BusinessNameController" must {

    "onPageLoad" must {
      "authorised users" must {
        "respond with OK" in new Setup {
          forAll(services) { (service) =>
            forAll(businessTypes) { businessType =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[BusinessType](
                      ArgumentMatchers.any()
                    )(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessNameWithAuthorisedUser(
                  controller,
                  businessType,
                  service
                )(result => status(result) must be(OK))
              }
            }
          }
        }
      }

      "respond with NotFound when invalid service is in uri" in new Setup {
        val serviceName = "INVALID_SERVICE"
        forAll(businessTypes) { (businessType) =>
          intercept[NotFoundException] {

            businessNameWithAuthorisedUser(
              controller,
              businessType,
              serviceName
            )(result => status(result) must be(NOT_FOUND))
          }
        }
      }
      "for corporations" when {
        val businessTypes = List("LTD", "UT", "ULTD", "UIB")
        "return Corporation Tax UTR Form view for a user" in new Setup {
          forAll(services) { (service) =>
            forAll(businessTypes) { businessType =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[BusinessType](
                      ArgumentMatchers.any()
                    )(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessNameWithAuthorisedUser(
                  controller,
                  businessType,
                  service
                ) { result =>
                  val document = Jsoup.parse(contentAsString(result))

                  document.title() must be(
                    s"${messages("bc.business-verification.businessNameField")} - ${getServiceMessageKey
                        .getOrElse(service, "")} - GOV.UK"
                  )
                  document
                    .getElementsByClass("govuk-caption-xl")
                    .text() must be(
                    s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
                  )
                  document
                    .getElementsByClass("govuk-heading-xl")
                    .text() must include(
                    s"${messages("bc.business-verification.businessNameField")}"
                  )
                  document.select("#businessName-hint").text() must be(
                    s"${messages("bc.business-verification.registered-name.hint")}"
                  )
                  document.getElementById("businessName").className() must be(
                    "govuk-input"
                  )
                }
              }
            }
          }
        }
        "Company Name field must not be empty" in new Setup {
          forAll(services) { (service) =>
            forAll(businessTypes.tail) { businessType =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType)
                )
              ) {
                when(
                  mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                  )
                ).thenReturn(Future.successful(None))

                submitWithAuthorisedUserJson(
                  controller,
                  businessType,
                  Map("businessName" -> ""),
                  service
                ) { result =>
                  status(result) must be(BAD_REQUEST)
                  val document = Jsoup.parse(contentAsString(result))
                  document.getElementsByClass("govuk-error-message").text() must include(messages("bc.business-verification-error.businessName"))
                }
              }
            }
          }
        }
      }
      "for partnerships" when {
        val partnershipTypes = List("LP", "LLP", "OBP")
        "return Partnership Tax UTR Form view for a user" in new Setup {
          forAll(services) { (service) =>
            forAll(partnershipTypes) { businessType =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[BusinessType](
                      ArgumentMatchers.any()
                    )(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessNameWithAuthorisedUser(
                  controller,
                  businessType,
                  service
                ) { result =>
                  val document = Jsoup.parse(contentAsString(result))

                  document.title() must be(
                    s"${messages("bc.business-verification.partnershipNameField")} - ${getServiceMessageKey
                        .getOrElse(service, "")} - GOV.UK"
                  )
                  document
                    .getElementsByClass("govuk-caption-xl")
                    .text() must be(
                    s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
                  )
                  document
                    .getElementsByClass("govuk-heading-xl")
                    .text() must include(
                    s"${messages("bc.business-verification.partnershipNameField")}"
                  )
                  document.select("#businessName-hint").text() must be(
                    s"${messages("bc.business-verification.registered-name.hint")}"
                  )
                  document.getElementById("businessName").className() must be(
                    "govuk-input"
                  )
                }
              }
            }
          }
        }
          "Partnership Name field must not be empty" in new Setup {
            forAll(services) { (service) =>
              forAll(partnershipTypes) { partnershipTypes =>
                if (
                  getBusinessTypesForService(service).exists(
                    _.equalsIgnoreCase(partnershipTypes)
                  )
                ) {
                  when(
                    mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                  ).thenReturn(Future.successful(None))

                  submitWithAuthorisedUserJson(
                    controller,
                    partnershipTypes,
                    Map("businessName" -> ""),
                    service
                  ) { result =>
                    status(result) must be(BAD_REQUEST)
                    val document = Jsoup.parse(contentAsString(result))
                    document.getElementsByClass("govuk-error-message").text() must include(messages("bc.business-verification-error.businessPartnerName"))
                  }
                }
              }
            }
          }
        "for sole traders" when {
          val businessTypes = List("SOP")
          "return SA Tax UTR Form view for a user" in new Setup {
            val services: Seq[String] = List("AWRS", "AMLS", "FHDDS")
            forAll(businessTypes) { (businessType) =>
              forAll(services) { (service) =>
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[BusinessType](
                      ArgumentMatchers.any()
                    )(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessNameWithAuthorisedSaUser(
                  controller,
                  businessType,
                  service
                ) { result =>
                  val document = Jsoup.parse(contentAsString(result))

                  document.title() must be(
                    s"${messages("bc.business-verification.SoleNameField")} - ${getServiceMessageKey
                        .getOrElse(service, "")} - GOV.UK"
                  )
                  document
                    .getElementsByClass("govuk-caption-xl")
                    .text() must be(
                    s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.client.text", service.toUpperCase)}"
                  )
                  document
                    .getElementsByClass("govuk-heading-xl")
                    .text() must include(
                    s"${messages("bc.business-verification.SoleNameField")}"
                  )
                  document.getElementById("firstName").className() must be(
                    "govuk-input"
                  )
                  document.getElementById("lastName").className() must be(
                    "govuk-input"
                  )
                }
              }
            }
          }
        }
      }
      "authorised agents" must {

        "respond with OK" in new Setup {
          forAll(services) { (service) =>
            forAll(businessTypes) { businessType =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[BusinessType](
                      ArgumentMatchers.any()
                    )(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessNameWithAuthorisedAgent(
                  controller,
                  businessType,
                  service
                )(result => status(result) must be(OK))
              }
            }
          }
        }
        "respond with NotFound when invalid service is in uri" in new Setup {
          val serviceName = "INVALID_SERVICE"
          forAll(businessTypes) { (businessType) =>
            intercept[NotFoundException] {

              businessNameWithAuthorisedAgent(
                controller,
                businessType,
                serviceName
              )(result => status(result) must be(NOT_FOUND))
            }
          }
        }
        "return Correct view for agent" in new Setup {
          val businessTypes = List("LTD", "UT", "ULTD", "UIB")
          forAll(services) { (service) =>
            forAll(businessTypes) { businessType =>
              if (
                getBusinessTypesForService(service).exists(
                  _.equalsIgnoreCase(businessType)
                )
              ) {
                when(
                  mockBusinessRegCacheConnector
                    .fetchAndGetCachedDetails[BusinessType](
                      ArgumentMatchers.any()
                    )(
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any(),
                      ArgumentMatchers.any()
                    )
                )
                  .thenReturn(Future.successful(None))
                businessNameWithAuthorisedAgent(
                  controller,
                  businessType,
                  service
                ) { result =>
                  val document = Jsoup.parse(contentAsString(result))

                  document.title() must be(
                    s"${messages("bc.business-verification-selected-agent-header")} - ${getServiceMessageKey
                        .getOrElse(service, "")} - GOV.UK"
                  )
                  document
                    .getElementsByClass("govuk-caption-xl")
                    .text() must be(
                    s"${messages("bc.screen-reader.section")} ${messages("bc.business-verification.agent.text", service.toUpperCase)}"
                  )
                  document
                    .getElementsByClass("govuk-heading-xl")
                    .text() must include(
                    messages(
                      "bc.business-verification.agent.text",
                      service.toUpperCase
                    )
                  )
                  document.select("#businessName-hint").text() must be(
                    s"${messages("bc.business-verification.registered-name.hint")}"
                  )
                  document
                    .getElementsByClass("govuk-warning-text__text")
                    .text() must be(
                    s"Warning ${messages("business-review.check-agency.text")}"
                  )
                  document.getElementById("businessName").className() must be(
                    "govuk-input"
                  )
                }
              }
            }
          }
        }
      }
      "unauthorised users" must {
        "respond with a redirect & be redirected to the unauthorised page" in new Setup {
          forAll(businessTypes) { (businessType) =>
            forAll(services) { (service) =>
              businessNameWithUnAuthorisedUser(
                controller,
                businessType,
                service
              ) { result =>
                status(result) must be(SEE_OTHER)
                redirectLocation(result).get must include(
                  "/business-customer/unauthorised"
                )
              }
            }
          }
        }
      }
      "return successful response with previously cached data for businesses" in new Setup {
        forAll(services) { (service) =>
          forAll(businessTypes.tail) { businessType =>
            if (
              getBusinessTypesForService(service).exists(
                _.equalsIgnoreCase(businessType)
              )
            ) {
              when(
                mockBusinessRegCacheConnector
                  .fetchAndGetCachedDetails[BusinessName](
                    ArgumentMatchers.any()
                  )(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                  )
              ).thenReturn(
                Future.successful(
                  Some(
                    BusinessName("Some Business Name")
                  )
                )
              )
              businessNameWithAuthorisedUser(
                controller,
                businessType,
                service
              ) { result =>
                status(result) must be(OK)
              }
            }
          }
        }
      }
      "return successful response with previously cached data for Sole Trader" in new Setup {
        forAll(services.tail) { (service) =>
          val businessType = "SOP"
          when(
            mockBusinessRegCacheConnector
              .fetchAndGetCachedDetails[SoleTraderName](
                ArgumentMatchers.any()
              )(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )
          ).thenReturn(
            Future.successful(
              Some(
                SoleTraderName("John", "Doe")
              )
            )
          )
          businessNameWithAuthorisedSaUser(controller, businessType, service) { result =>
            status(result) must be(OK)
          }
        }
      }

      "return successful response for Limited Partnership business type during AWRS journey without previous cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[BusinessName](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessNameWithAuthorisedUser(controller, "LP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Partnership business type during neither AWRS nor ATED journey" in new Setup {
        businessNameWithAuthorisedUser(controller, "LP", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Ordinary Business Partnership type during neither AWRS nor ATED journey" in new Setup {
        businessNameWithAuthorisedAgent(controller, "OBP", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Ordinary Business Partnership business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[BusinessName](
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
                BusinessName(
                  "TestBusinessName"
                )
              )
            )
          )
        businessNameWithAuthorisedAgent(controller, "OBP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Liability Partnership business type during AWRS journey without previous cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[BusinessName](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(Future.successful(None))
        businessNameWithAuthorisedAgent(controller, "LLP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Liability Partnership business type during neither AWRS nor ATED journey" in new Setup {
        businessNameWithAuthorisedAgent(controller, "LLP", "amls") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Liability Partnership business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[BusinessName](
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
                BusinessName(
                  "TestBusinessName"
                )
              )
            )
          )
        businessNameWithAuthorisedAgent(controller, "LLP", "awrs") { result =>
          status(result) must be(OK)
        }
      }

      "return successful response for Limited Partnership business type during AWRS journey with previously cached data" in new Setup {
        when(
          mockBusinessRegCacheConnector
            .fetchAndGetCachedDetails[BusinessName](
              ArgumentMatchers.any()
            )(
              ArgumentMatchers.any(),
              ArgumentMatchers.any(),
              ArgumentMatchers.any()
            )
        )
          .thenReturn(
            Future.successful(
              Some(BusinessName("TestBusinessName"))
            )
          )
        businessNameWithAuthorisedAgent(controller, "LP", "awrs") { result =>
          status(result) must be(OK)
        }
      }
    }

    "submit" must {
      "redirect to utr Page for businesses" in new Setup {
        forAll(services) { (service) =>
          forAll(businessTypes.tail) { businessType =>
            if (
              getBusinessTypesForService(service).exists(
                _.equalsIgnoreCase(businessType)
              )
            ) {
              val businessName                            = "Some trading company"
              val businessNameValues: Map[String, String] = Map(
                "businessName" -> businessName
              )
              when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(Future.successful(None))
              when(
                mockBusinessRegCacheConnector
                  .cacheDetails[BusinessName](
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                  )(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                  )
              ).thenReturn(Future.successful(BusinessName(businessName)))
              submitWithAuthorisedUserJson(
                controller,
                s"$businessType",
                businessNameValues,
                service
              ) { result =>
                //status(result) must be(SEE_OTHER)
                redirectLocation(result).get must include(
                  s"/business-verification/$service/businessFormUtr/$businessType"
                )
              }
            }
          }
        }
      }
      "redirect to utr Page for Sole Traders" in new Setup {
        forAll(services.tail) { (service) =>
          val businessType                       = "SOP"
          val sopNameValues: Map[String, String] = Map(
            "firstName" -> "John",
            "lastName"  -> "Doe"
          )
          when(mockBackLinkCache.saveBackLink(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(Future.successful(None))
          when(
            mockBusinessRegCacheConnector
              .cacheDetails[SoleTraderName](
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
              )
          ).thenReturn(Future.successful(SoleTraderName("", "")))
          submitWithAuthorisedUserJson(
            controller,
            s"$businessType",
            sopNameValues,
            service
          ) { result =>
            status(result) must be(SEE_OTHER)
            redirectLocation(result).get must include(
              s"/business-verification/$service/businessFormUtr/$businessType"
            )
          }
        }
      }
      "First Name field must not be empty for Sole Trader" in new Setup {
        when(
          mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )
        ).thenReturn(Future.successful(None))

        submitWithAuthorisedUserJson(
          controller,
          "SOP",
          Map(
            "firstName" -> "",
            "lastName"  -> "Smith"
          ),
          "AWRS"
        ) { result =>
          status(result) must be(BAD_REQUEST)
          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-error-message").text() must include(messages("bc.business-verification-error.firstname"))
        }
      }
      "Last Name field must not be empty for Sole Trader" in new Setup {
        when(
          mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )
        ).thenReturn(Future.successful(None))

        submitWithAuthorisedUserJson(
          controller,
          "SOP",
          Map(
            "firstName" -> "Alan",
            "lastName"  -> ""
          ),
          "AWRS"
        ) { result =>
          status(result) must be(BAD_REQUEST)
          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-error-message").text() must include(messages("bc.business-verification-error.surname"))
        }
      }
      "Name fields must not be empty for Sole Trader" in new Setup {
        when(
          mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )
        ).thenReturn(Future.successful(None))

        submitWithAuthorisedUserJson(
          controller,
          "SOP",
          Map(
            "firstName" -> "",
            "lastName"  -> ""
          ),
          "AWRS"
        ) { result =>
          status(result) must be(BAD_REQUEST)
          val document = Jsoup.parse(contentAsString(result))
          document.getElementsByClass("govuk-error-message").text() must include(messages("bc.business-verification-error.surname"))
          document.getElementsByClass("govuk-error-message").text() must include(messages("bc.business-verification-error.firstname"))
        }
      }
      "handle the name submission validation correctly" must {
        "if the selection is an Organisation" must {
          formValidationNameInputDataSetOrg foreach { dataSet =>
            s"${dataSet._1}" must {
              dataSet._2 foreach { inputData =>
                s"${inputData._1}" in new Setup {
                  submitWithAuthorisedUserJson(
                    controller,
                    inputData._2,
                    inputData._3,
                    "ATED"
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
          formValidationNameInputDataSetInd foreach { dataSet =>
            s"${dataSet._1}" in new Setup {
              submitWithAuthorisedUserSuccessInd(
                dataSet._2,
                dataSet._3,
                controller,
                "AWRS"
              ) { result =>
                status(result) must be(BAD_REQUEST)
                contentAsString(result) must include(s"${dataSet._4}")
              }
            }
          }
        }
      }
    }
  }
  def submitWithAuthorisedUserSuccessOrg(
                                          businessType: String,
                                          fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
                                          controller: BusinessNameController,
                                          service: String
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
    ).thenReturn(Future.successful(None))

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
 def submitWithAuthorisedUserSuccessInd(
                                          businessType: String,
                                          fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded],
                                          controller: BusinessNameController,
                                          service: String
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
    ).thenReturn(Future.successful(None))

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


  def businessNameWithAuthorisedAgent(
    controller: BusinessNameController,
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
  def businessNameWithAuthorisedUser(
    controller: BusinessNameController,
    businessType: String,
    service: String
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
  def businessNameWithAuthorisedSaUser(
    controller: BusinessNameController,
    businessType: String,
    service: String
  )(test: Future[Result] => Any): Unit = {
    val sessionId = s"session-${UUID.randomUUID}"
    val userId    = s"user-${UUID.randomUUID}"

    AuthBuilder.mockAuthorisedSaUser(userId, mockAuthConnector)
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
  def businessNameWithUnAuthorisedUser(
    controller: BusinessNameController,
    businessType: String,
    service: String
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

  def submitWithAuthorisedUserJson(
    controller: BusinessNameController,
    businessType: String,
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
    AuthBuilder.mockAuthorisedUser(userId, mockAuthConnector)
    when(
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller.submit(service, businessType).apply(generateRequest)

    test(result)
  }

  def submitWithAuthorisedSaUserJson(
    controller: BusinessNameController,
    businessType: String,
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
      mockBackLinkCache.fetchAndGetBackLink(ArgumentMatchers.any())(
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )
    ).thenReturn(Future.successful(None))

    val result = controller.submit(service, businessType).apply(generateRequest)

    test(result)
  }
}
