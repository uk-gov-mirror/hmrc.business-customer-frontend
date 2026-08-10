/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}

trait BusinessMatchTestObjects {

  val matchUtr: SaUtr   = new SaUtrGenerator().nextSaUtr
  val noMatchUtr: SaUtr = new SaUtrGenerator().nextSaUtr

  val formValidationNameInputDataSetOrg: Seq[
    (
      MustTestMessage,
      Seq[(InTestMessage, BusinessType, NameRequest, ErrorMessage)]
    )
  ] =
    Seq(
      (
        "if the selection is Unincorporated body :",
        Seq(
          (
            "Business Name must not be empty",
            "UIB",
            businessNameRequest(businessName = ""),
            "Enter a registered company name"
          ),
          (
            "Registered Name must not be more than 105 characters",
            "UIB",
            businessNameRequest(businessName = "a" * 106),
            "The registered company name cannot be more than 105 characters"
          )
        )
      ),
      (
        "if the selection is Limited Company :",
        Seq(
          (
            "Business Name must not be empty",
            "LTD",
            businessNameRequest(businessName = ""),
            "Enter a registered company name"
          ),
          (
            "Registered Name must not be more than 105 characters",
            "LTD",
            businessNameRequest(businessName = "a" * 106),
            "The registered company name cannot be more than 105 characters"
          )
        )
      ),
      (
        "if the selection is Non Resident Landlord :",
        Seq(
          (
            "Business Name must not be empty",
            "NRL",
            businessNameRequest(businessName = ""),
            "Enter a registered company name"
          )
        )
      ),
      (
        "if the selection is Limited Liability Partnership : ",
        Seq(
          (
            "Partnership Name must not be empty",
            "LLP",
            businessNameRequest(businessName = ""),
            "Enter a registered partnership name"
          ),
          (
            "Registered Name must not be more than 105 characters",
            "LLP",
            businessNameRequest(businessName = "a" * 106),
            "The registered partnership name cannot be more than 105 characters"
          )
        )
      ),
      (
        "if the selection is Limited Partnership : ",
        Seq(
          (
            "Business Name must not be empty",
            "LP",
            businessNameRequest(businessName = ""),
            "Enter a registered partnership name"
          ),
          (
            "Registered Name must not be more than 105 characters",
            "LP",
            businessNameRequest(businessName = "a" * 106),
            "The registered partnership name cannot be more than 105 characters"
          )
        )
      ),
      (
        "if the selection is Ordinary Business Partnership : ",
        Seq(
          (
            "Business Name must not be empty",
            "OBP",
            businessNameRequest(businessName = ""),
            "Enter a registered partnership name"
          ),
          (
            "Registered Name must not be more than 105 characters",
            "OBP",
            businessNameRequest(businessName = "a" * 106),
            "The registered partnership name cannot be more than 105 characters"
          )
        )
      )
    )
  type InputRequest    = FakeRequest[AnyContentAsFormUrlEncoded]
  type NameRequest    = Map[String, String]
  type MustTestMessage = String
  type InTestMessage   = String
  type ErrorMessage    = String
  type BusinessType    = String

  def nrlUtrRequest(
    utr: String,
  ): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest("POST", "/").withFormUrlEncodedBody(
      "utr"        -> s"$utr",
    )
  def businessNameRequest(
    businessName: String
  ): Map[String, String] =
    Map("businessName" -> businessName)

  def utrRequest(
    utr: String,
  ): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest("POST", "/").withFormUrlEncodedBody(
      "utr"       -> s"$utr",
    )

  def saUtrRequest(
    utr: String,
  ): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest("POST", "/").withFormUrlEncodedBody(
      "utr"     -> s"$utr",
    )
  def saNameRequest(
    firstName: String,
    lastName: String
  ): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest("POST", "/").withFormUrlEncodedBody(
      "firstName"     -> s"$firstName",
      "lastName"     -> s"$lastName",
    )

  val formValidationInputUTRDataSetOrg: Seq[
    (
      MustTestMessage,
      Seq[(InTestMessage, BusinessType, InputRequest, ErrorMessage)]
    )
  ] =
    Seq(
      (
        "if the selection is Unincorporated body :",
        Seq(
          (
            "CO Tax UTR must not be empty",
            "UIB",
            utrRequest(utr = ""),
            "Enter a Corporation Tax Unique Taxpayer Reference"
          ),
          (
            "CO Tax UTR must be 10 digits",
            "UIB",
            utrRequest(utr = "1" * 11),
            "Enter a 10-digit Unique Taxpayer Reference (UTR). If your UTR is 13 digits, enter only the last 10 digits"
          ),
          (
            "CO Tax UTR must contain only digits",
            "UIB",
            utrRequest(utr = "12345678aa"),
            "Enter a 10-digit Unique Taxpayer Reference (UTR). If your UTR is 13 digits, enter only the last 10 digits"
          ),
          (
            "CO Tax UTR must be valid",
            "UIB",
            utrRequest(utr = "1234567890"),
            "The Corporation Tax Unique Taxpayer Reference is not valid"
          )
        )
      ),
      (
        "if the selection is Limited Company :",
        Seq(
          (
            "CO Tax UTR must not be empty",
            "LTD",
            utrRequest(utr = ""),
            "Enter a Corporation Tax Unique Taxpayer Reference"
          ),
          (
            "CO Tax UTR must be 10 digits",
            "LTD",
            utrRequest(utr = "1" * 11),
            "Enter a 10-digit Unique Taxpayer Reference (UTR). If your UTR is 13 digits, enter only the last 10 digits"
          ),
          (
            "CO Tax UTR must contain only digits",
            "LTD",
            utrRequest(utr = "12345678aa"),
            "Enter a 10-digit Unique Taxpayer Reference (UTR). If your UTR is 13 digits, enter only the last 10 digits"
          ),
          (
            "CO Tax UTR must be valid",
            "LTD",
            utrRequest(utr = "1234567890"),
            "The Corporation Tax Unique Taxpayer Reference is not valid"
          )
        )
      ),
      (
        "if the selection is Non Resident Landlord :",
        Seq(
          (
            "SA UTR must not be empty",
            "NRL",
            nrlUtrRequest(utr = ""),
            "Enter a Self Assessment Unique Taxpayer Reference"
          ),
          (
            "SA UTR must be 10 digits",
            "NRL",
            nrlUtrRequest(utr = "12345678901"),
            "Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "SA UTR must contain only digits",
            "NRL",
            nrlUtrRequest(utr = "12345678aa"),
            "Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "SA UTR must be valid",
            "NRL",
            nrlUtrRequest(utr = "1234567890"),
            "The Self Assessment Unique Taxpayer Reference is not valid"
          )
        )
      ),
      (
        "if the selection is Limited Liability Partnership : ",
        Seq(
          (
            "Partnership Self Assessment UTR  must not be empty",
            "LLP",
            utrRequest(utr = ""),
            "Enter a Partnership Self Assessment Unique Taxpayer Reference"
          ),
          (
            "Partnership Self Assessment UTR  must be 10 digits",
            "LLP",
            utrRequest(utr = "1" * 11),
            "Partnership Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "Partnership Self Assessment UTR  must contain only digits",
            "LLP",
            utrRequest(utr = "12345678aa"),
            "Partnership Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "Partnership Self Assessment UTR  must be valid",
            "LLP",
            utrRequest(utr = "1234567890"),
            "The Partnership Self Assessment Unique Taxpayer Reference is not valid"
          )
        )
      ),
      (
        "if the selection is Limited Partnership : ",
        Seq(
          (
            "Partnership Self Assessment UTR  must not be empty",
            "LP",
            utrRequest(utr = ""),
            "Enter a Partnership Self Assessment Unique Taxpayer Reference"
          ),
          (
            "Partnership Self Assessment UTR  must be 10 digits",
            "LP",
            utrRequest(utr = "1" * 11),
            "Partnership Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "Partnership Self Assessment UTR  must contain only digits",
            "LP",
            utrRequest(utr = "12345678aa"),
            "Partnership Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "Partnership Self Assessment UTR  must be valid",
            "LP",
            utrRequest(utr = "1234567890"),
            "The Partnership Self Assessment Unique Taxpayer Reference is not valid"
          )
        )
      ),
      (
        "if the selection is Ordinary Business Partnership : ",
        Seq(
          (
            "Partnership Self Assessment UTR  must not be empty",
            "OBP",
            utrRequest(utr = ""),
            "Enter a Partnership Self Assessment Unique Taxpayer Reference"
          ),
          (
            "Partnership Self Assessment UTR  must be 10 digits",
            "OBP",
            utrRequest(utr = "1" * 11),
            "Partnership Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "Partnership Self Assessment UTR  must contain only digits",
            "OBP",
            utrRequest(utr = "12345678aa"),
            "Partnership Self Assessment Unique Taxpayer Reference must be 10 digits"
          ),
          (
            "Partnership Self Assessment UTR  must be valid",
            "OBP",
            utrRequest(utr = "1234567890"),
            "The Partnership Self Assessment Unique Taxpayer Reference is not valid"
          )
        )
      )
    )

  val formValidationNameInputDataSetInd: Seq[(InTestMessage, BusinessType, InputRequest, ErrorMessage)] =
    Seq(
      (
        "First name must not be empty",
        "SOP",
        saNameRequest(firstName = "", lastName = "wright"),
        "Enter a first name"
      ),
      (
        "Last name must not be empty",
        "SOP",
        saNameRequest(firstName = "bob", lastName = ""),
        "Enter a last name"
      ),
      (
        "First Name must not be more than 40 characters",
        "SOP",
        saNameRequest(firstName = "a" * 41, lastName = "wright"),
        "A first name cannot be more than 40 characters"
      ),
      (
        "Last Name must not be more than 40 characters",
        "SOP",
        saNameRequest(firstName = "bob", lastName = "a" * 41),
        "A last name cannot be more than 40 characters"
      )
    )

  val formValidationInputUtrDataSetInd: Seq[(InTestMessage, BusinessType, InputRequest, ErrorMessage)] = {
    Seq(
      (
        "SA UTR must not be empty",
        "SOP",
        saUtrRequest(utr = ""),
        "Enter a Self Assessment Unique Taxpayer Reference"
      ),
      (
        "SA UTR must be 10 digits",
        "SOP",
        saUtrRequest(utr = "12345678901"),
        "Self Assessment Unique Taxpayer Reference must be 10 digits"
      ),
      (
        "SA UTR must contain only digits",
        "SOP",
        saUtrRequest(utr = "12345678aa"),
        "Self Assessment Unique Taxpayer Reference must be 10 digits"
      ),
      (
        "SA UTR must be valid",
        "SOP",
        saUtrRequest(utr = "1234567890"),
        "The Self Assessment Unique Taxpayer Reference is not valid"
      )
    )
  }
}
