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

package config

import java.util.{Collections, PropertyResourceBundle}
import play.api.Environment

import java.io.InputStreamReader
import scala.jdk.CollectionConverters
import scala.util.{Success, Try}

trait BCUtils {

  val environment: Environment

  val (zero, one, two, three, four, five, six, seven, eight, nine, ten, eleven) = (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

  lazy val resourceStream: PropertyResourceBundle =
    (environment.resourceAsStream("country-code.properties") flatMap { stream =>

      val inputStreamReader: InputStreamReader = new InputStreamReader(stream, "UTF-8")
      val optBundle: Option[PropertyResourceBundle] = Try(new PropertyResourceBundle(inputStreamReader)) match {
        case Success(bundle) => Some(bundle)
        case _               => None
      }
      stream.close()
      optBundle
    }).getOrElse(throw new RuntimeException("[BCUtils] Could not retrieve property bundle"))

  def validateUTR(utr: String): Boolean = {
    utr.trim.length == ten && utr.trim.forall(_.isDigit) && {
      val actualUtr = utr.trim.toList
      val checkDigit = actualUtr.head.asDigit
      val restOfUtr = actualUtr.tail
      val weights = List(six, seven, eight, nine, ten, five, four, three, two)
      val weightedUtr = for ((w1, u1) <- weights zip restOfUtr) yield w1 * u1.asDigit
      val total = weightedUtr.sum
      val remainder = total % eleven
      isValidUtr(remainder, checkDigit)
    }
  }

  private def isValidUtr(remainder: Int, checkDigit: Int): Boolean = {
    val mapOfRemainders = Map(
      zero -> two, one -> one, two -> nine, three -> eight, four -> seven, five -> six,
      six -> five, seven -> four, eight -> three, nine -> two, ten -> one)
    mapOfRemainders.get(remainder).contains(checkDigit)
  }

  def getIsoCodeTupleList: List[(String, String)] = {
    CollectionConverters.IterableHasAsScala(Collections.list(resourceStream.getKeys)).asScala
      .toList.map(key => (key, resourceStream.getString(key))).sortBy{case (_,v) => v}
  }

  def getNavTitle(serviceName: String): Option[String] = {
    serviceName.toLowerCase match {
      case "ated" => Some("bc.ated.serviceName")
      case "awrs" => Some("bc.awrs.serviceName")
      case "amls" => Some("bc.amls.serviceName")
      case "fhdds" => Some("bc.fhdds.serviceName")
      case _ => None
    }
  }

  def businessTypeMap(service: String, isAgent: Boolean): Seq[(String, String)] = {
    val fixedBusinessTypes = Seq(
      "SOP" -> "bc.business-verification.SOP", "LTD" -> "bc.business-verification.LTD",
      "OBP" -> "bc.business-verification.PRT", "LP" -> "bc.business-verification.LP",
      "LLP" -> "bc.business-verification.LLP", "UIB" -> "bc.business-verification.UIB"
    )
    val isAtedAgentBusinessTypes = Seq(
      "LTD" -> "bc.business-verification.LTD", "LLP" -> "bc.business-verification.LLP",
      "SOP" -> "bc.business-verification.SOP", "OBP" -> "bc.business-verification.PRT",
      "UIB" -> "bc.business-verification.UIB", "LP" -> "bc.business-verification.LP",
      "ULTD" -> "bc.business-verification.ULTD", "NUK" -> "bc.business-verification.NUK"
    )
    val atedExtraBusinessTypes = Seq(
      "UT" -> "bc.business-verification.UT", "ULTD" -> "bc.business-verification.ULTD",
      "NUK" -> "bc.business-verification.agent.NUK"
    )

    def handleAted: Seq[(String, String)] = {
      if (isAgent) {
        isAtedAgentBusinessTypes.filterNot { case (code, _) => code == "UIB" }
      } else {
        fixedBusinessTypes.filterNot { case (code, _) => code == "UIB" || code == "SOP" } ++ atedExtraBusinessTypes
      }
    }

    service.toLowerCase match {
      case "awrs" => Seq(
        "OBP" -> "bc.business-verification.PRT", "GROUP" -> "bc.business-verification.GROUP", "LTD" -> "bc.business-verification.LTD",
        "LLP" -> "bc.business-verification.LLP", "LP" -> "bc.business-verification.LP",
        "SOP" -> "bc.business-verification.SOP", "UIB" -> "bc.business-verification.UIB"
      )
      case "amls" => Seq(
        "LTD" -> "bc.business-verification.LTD", "SOP" -> "bc.business-verification.amls.SOP",
        "OBP" -> "bc.business-verification.amls.PRT", "LLP" -> "bc.business-verification.amls.LP.LLP",
        "UIB" -> "bc.business-verification.amls.UIB"
      )
      case "ated" => handleAted
      case _ => fixedBusinessTypes
    }
  }

  def getSelectedCountry(isoCode: String): String = {

    def trimCountry(selectedCountry: String): String = {
      val position = selectedCountry.indexOf(":")
      if (position > zero) selectedCountry.substring(0, position).trim else selectedCountry
    }

    def getCountry(isoCode: String): Option[String] = {
      val country = Try {
        resourceStream.getString(isoCode.toUpperCase())
      } match {
        case Success(s) => Some(s)
        case _ => None
      }
      country.map(selectedCountry => trimCountry(selectedCountry))
    }

    getCountry(isoCode.toUpperCase).getOrElse(isoCode)
  }

  def validateGroupId(str: String): String = {
    if(str.trim.length != 36) {
      if(str.contains("testGroupId-")){
        str.replace("testGroupId-", "")
      } else throw new RuntimeException("Invalid groupId from auth")
    } else str.trim
  }
}
