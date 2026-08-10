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

package forms


import config.BCUtils
import play.api.Environment
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json.{Format, Json}

import scala.util.matching.Regex

case class BusinessName(businessName: String)

object BusinessName {
  implicit val formats: Format[BusinessName] = Json.format[BusinessName]
}

case class SoleTraderName(firstName: String, lastName: String)

object SoleTraderName {
  implicit val formats: Format[SoleTraderName] = Json.format[SoleTraderName]
}

case class Utr(utr: String)

object Utr {
  implicit val formats: Format[Utr] = Json.format[Utr]
}

case class BusinessType(businessType: Option[String] = None, isSaAccount: Boolean, isOrgAccount: Boolean)

object BusinessType {
  implicit val formats: Format[BusinessType] = Json.format[BusinessType]
}

object BusinessVerificationForms extends BCUtils {
  val selfAssessment: Seq[String] = Seq("SOP", "NRL")
  val corporations: Seq[String]   = Seq("LTD", "UT", "ULTD", "UIB")
  val partnerships: Seq[String]   = Seq("OBP", "LP", "LLP")

  override val environment: Environment = Environment.simple()

  val length40 = 40
  val length0 = 0
  val length105 = 105
  val utrRegex: Regex = """^[0-9]{10}$""".r

  def validateBusinessType(businessTypeData: Form[BusinessType], service: String): Form[BusinessType] = {
    val isSaAccount = businessTypeData.data.get("isSaAccount").fold(false)(_.toBoolean)
    val isOrgAccount = businessTypeData.data.get("isOrgAccount").fold(false)(_.toBoolean)
    val businessType = businessTypeData.data.get("businessType") map (_.trim) filterNot (_.isEmpty)

    (businessType.nonEmpty, businessType.getOrElse(""), isSaAccount, isOrgAccount) match {
      case (true, "SOP", false, true) =>
        service match {
          case "amls" => businessTypeData
          case _ => businessTypeData.withError(
            key = "businessType",
            message = "bc.business-verification-error.type_of_business_organisation_invalid")
        }
      case (true, "SOP", true, false) => businessTypeData
      case (true, _, true, false) =>
        businessTypeData.withError(
          key = "businessType",
          message = "bc.business-verification-error.type_of_business_individual_invalid"
        )
      case _ => businessTypeData
    }
  }

  val businessTypeForm: Form[BusinessType] ={
    Form(mapping(
    "businessType" -> optional(text).verifying("bc.business-verification-error.not-selected", x => x.isDefined),
    "isSaAccount" -> boolean,
    "isOrgAccount" -> boolean
  )(BusinessType.apply)(BusinessType.unapply)
  )
}

  def businessName(businessType: String): Form[BusinessName] ={
    val businessTypeKey: String = if (partnerships.contains(businessType)) "businessPartner" else "business"
    Form(mapping(
      "businessName" -> text
        .verifying(s"bc.business-verification-error.${businessTypeKey}Name", x => x.trim.length > length0)
        .verifying(s"bc.business-verification-error.${businessTypeKey}Name.length", x => x.isEmpty || (x.nonEmpty && x.length <= length105))
    )(BusinessName.apply)(BusinessName.unapply))}

  val soleTraderNameForm: Form[SoleTraderName] = Form(mapping(
    "firstName" -> text
      .verifying("bc.business-verification-error.firstname", x => x.trim.length > length0)
      .verifying("bc.business-verification-error.firstname.length", x => x.isEmpty || (x.nonEmpty && x.length <= length40)),
    "lastName" -> text
      .verifying("bc.business-verification-error.surname", x => x.trim.length > length0)
      .verifying("bc.business-verification-error.surname.length", x => x.isEmpty || (x.nonEmpty && x.length <= length40))
  )(SoleTraderName.apply)(SoleTraderName.unapply))

  val saUtr: Form[Utr] = Form(mapping(
    "utr" -> text
      .verifying("bc.business-verification-error.saUtr", x => x.replaceAll(" ", "").length > length0)
      .verifying("bc.business-verification-error.saUtr.length", x => {
        val trimmedString = x.replaceAll(" ", "")
        trimmedString.isEmpty || (trimmedString.nonEmpty && trimmedString.matches(utrRegex.regex))}
      )
      .verifying("bc.business-verification-error.invalidSAUTR", x => {
        val trimmedString = x.replaceAll(" ", "")
        trimmedString.isEmpty || (validateUTR(trimmedString) || !trimmedString.matches(utrRegex.regex))
      })
  )(Utr.apply)(Utr.unapply))

  val cotaxUtr: Form[Utr] = Form(mapping(
    "utr" -> text
      .verifying("bc.business-verification-error.cotaxutr", x => x.replaceAll(" ", "").length > length0)
      .verifying("bc.business-verification-error.cotaxutr.length", x => {
        val trimmedString = x.replaceAll(" ", "")
        trimmedString.isEmpty || (trimmedString.nonEmpty && trimmedString.matches(utrRegex.regex))}
      )
      .verifying("bc.business-verification-error.invalidCOUTR", x => {
          val trimmedString = x.replaceAll(" ", "")
          trimmedString.isEmpty || (validateUTR(trimmedString) || !trimmedString.matches(utrRegex.regex))
        })
  )(Utr.apply)(Utr.unapply))

  val psaUtr: Form[Utr] = Form(mapping(
    "utr" -> text
      .verifying("bc.business-verification-error.psaUtr", x => x.replaceAll(" ", "").length > length0)
      .verifying("bc.business-verification-error.psaUtr.length", x => {
        val trimmedString = x.replaceAll(" ", "")
        trimmedString.isEmpty || (trimmedString.nonEmpty && trimmedString.matches(utrRegex.regex))}
      )
      .verifying("bc.business-verification-error.invalidPSAUTR", x => {
        val trimmedString = x.replaceAll(" ", "")
        trimmedString.isEmpty || (validateUTR(trimmedString) || !trimmedString.matches(utrRegex.regex))
      })
  )(Utr.apply)(Utr.unapply))

}
