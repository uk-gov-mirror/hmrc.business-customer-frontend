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
import org.jsoup.Jsoup
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}

class ApplicationControllerSpec extends PlaySpec with GuiceOneServerPerSuite with Injecting {
  val service = "ATED"
  val injectedViewInstanceUnauthorised = inject[views.html.unauthorised]
  val injectedViewInstanceLogout = inject[views.html.logout]

  implicit val lang: Lang = Lang.defaultLang
  implicit val appConfig: ApplicationConfig = inject[ApplicationConfig]

  trait Setup {
    val controller = new ApplicationController(
      appConfig,
      injectedViewInstanceUnauthorised,
      injectedViewInstanceLogout,
      inject[MessagesControllerComponents]
    )
  }

  "ApplicationController" must {

    "unauthorised" must {

      "respond with an OK" in new Setup {
        val result = controller.unauthorised().apply(FakeRequest())
        status(result) must equal(OK)
      }

      "load the unauthorised page" in new Setup {
        val messagesApi: MessagesApi = inject[MessagesApi]
        implicit val messages : play.api.i18n.Messages = play.api.i18n.MessagesImpl(Lang.defaultLang, messagesApi)
        val result = controller.unauthorised().apply(FakeRequest())
        val content = contentAsString(result)
        val doc = Jsoup.parse(content)
        doc.title() must be(Messages("bc.unauthorised.title").concat(" - Register as an alcohol wholesaler or producer - GOV.UK"))
      }

    }

    "Cancel" must {

      "respond with a redirect" in new Setup {
        val result = controller.cancel().apply(FakeRequest())
        status(result) must be(SEE_OTHER)
      }

      "be redirected to the login page" in new Setup {
        val result = controller.cancel().apply(FakeRequest())
        redirectLocation(result).get must include("https://www.gov.uk/")
      }

    }

    "Not the right business link" must {

      "respond with a redirect" in new Setup {
        val result = controller.logoutAndRedirectToHome(service).apply(FakeRequest())
        status(result) must be(SEE_OTHER)
      }

      "be redirected to login page" in new Setup {
        val result = controller.logoutAndRedirectToHome(service).apply(FakeRequest())
        redirectLocation(result).get must include("/business-customer/agent/ATED")
      }

    }

    "Keep Alive" must {

      "respond with an OK" in new Setup {
        val result = controller.keepAlive.apply(FakeRequest())

        status(result) must be(OK)
      }
    }
    
    "Logout" must {

      "respond with a redirect" in new Setup {
        val result = controller.logout(service).apply(FakeRequest())
        status(result) must be(SEE_OTHER)
      }

      "be redirected to the feedback page for ATED service" in new Setup {
        val result = controller.logout(service).apply(FakeRequest())
        redirectLocation(result).get must include("/ated/logout")
      }

      "be redirected to the feedback page for AWRS service" in new Setup {
        val result = controller.logout("AWRS").apply(FakeRequest())
        redirectLocation(result).get must include("/alcohol-wholesale-scheme/logout")
      }

      "be redirected to the Bas Gateway page for AMLS service" in new Setup {
        val result = controller.logout("AMLS").apply(FakeRequest())
        redirectLocation(result).get must include("/bas-gateway/sign-out-without-state")
      }

      "be redirected to the feedback page for FHDDS service" in new Setup {
        val result = controller.logout("FHDDS").apply(FakeRequest())
        redirectLocation(result).get must include("/fhdds/sign-out")
      }

      "be redirected to the logout page for any other service other than ATED, AWRS and AMLS" in new Setup {
        val result = controller.logout("TEST").apply(FakeRequest())
        redirectLocation(result).get must include("/business-customer/signed-out")
      }

      "send to signed out page" in new Setup {
        val result = controller.signedOut().apply(FakeRequest())
        status(result) must be(OK)
      }

    }

    "TimedOut" must {

      "respond with a redirect" in new Setup {
        val result = controller.timedOut(service).apply(FakeRequest())
        status(result) must be(SEE_OTHER)
      }

      "be redirected to the feedback page for ATED service" in new Setup {
        val result = controller.timedOut(service).apply(FakeRequest())
        redirectLocation(result).get must include("/ated/logout")
      }

      "be redirected to the timed out page for AWRS service" in new Setup {
        val result = controller.timedOut("AWRS").apply(FakeRequest())
        redirectLocation(result).get must include("/alcohol-wholesale-scheme/timedOut")
      }

      "be redirected to the Bas Gateway page for AMLS service" in new Setup {
        val result = controller.timedOut("AMLS").apply(FakeRequest())
        redirectLocation(result).get must include("/bas-gateway/sign-out-without-state")
      }

      "be redirected to the feedback page for FHDDS service" in new Setup {
        val result = controller.timedOut("FHDDS").apply(FakeRequest())
        redirectLocation(result).get must include("/fhdds/sign-out")
      }

      "be redirected to the logout page for any other service other than ATED, AWRS and AMLS" in new Setup {
        val result = controller.timedOut("TEST").apply(FakeRequest())
        redirectLocation(result).get must include("/business-customer/signed-out")
      }
    }
  }
}
