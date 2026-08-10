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

package services

import connectors.{BusinessMatchingConnector, DataCacheConnector}
import models._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import utils.BusinessCustomerConstants.{CorporateBody, Partnership, SoleTrader}
import utils.SessionUtils

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class BusinessMatchingService @Inject()(val businessMatchingConnector: BusinessMatchingConnector,
                                        val dataCacheConnector: DataCacheConnector) {

  def matchBusinessWithUTR(isAnAgent: Boolean, service: String)
                          (implicit authContext: StandardAuthRetrievals, hc: HeaderCarrier, ec: ExecutionContext): Option[Future[JsValue]] = {
    getUserUtrAndType map { userUtrAndType =>
      val (userUTR, userType) = userUtrAndType
      val trimmedUtr = userUTR.replaceAll(" ", "")
      val searchData = MatchBusinessData(acknowledgementReference = SessionUtils.getUniqueAckNo,
        utr = trimmedUtr, isAnAgent = isAnAgent, individual = None, organisation = None)
      businessMatchingConnector.lookup(searchData, userType, service) flatMap { dataReturned =>
        (service.toLowerCase, userType) match {
          case (_, "org") => validateAndCache(dataReturned = dataReturned, directMatch = true, utr = Some(trimmedUtr), Some(CorporateBody))
          case ("ated", "sa") => validateAndCache(dataReturned = dataReturned, directMatch = true, utr = Some(trimmedUtr), Some(Partnership))
          case _ => validateAndCache(dataReturned = dataReturned, directMatch = true, utr = Some(trimmedUtr), None)
        }
      }
    }
  }

  def matchBusinessWithIndividualName(isAnAgent: Boolean, individual: Individual, saUTR: String, service: String)
                                     (implicit authContext: StandardAuthRetrievals, hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {

    val trimmedUtr = saUTR.replaceAll(" ", "")
    val searchData = MatchBusinessData(acknowledgementReference = SessionUtils.getUniqueAckNo,
      utr = trimmedUtr, requiresNameMatch = true, isAnAgent = isAnAgent, individual = Some(individual), organisation = None)
    val userType = "sa"
    businessMatchingConnector.lookup(searchData, userType, service) flatMap { dataReturned =>
      validateAndCache(dataReturned = dataReturned, directMatch = false, utr = Some(trimmedUtr), None)
    }
  }

  def matchBusinessWithOrganisationName(isAnAgent: Boolean, organisation: Organisation, utr: String, service: String)
                                       (implicit authContext: StandardAuthRetrievals, hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
    val trimmedUtr = utr.replaceAll(" ", "")
    val searchData = MatchBusinessData(acknowledgementReference = SessionUtils.getUniqueAckNo,
      utr = trimmedUtr, requiresNameMatch = true, isAnAgent = isAnAgent, individual = None, organisation = Some(organisation))
    val userType = "org"
    val orgType = organisation.organisationType
    businessMatchingConnector.lookup(searchData, userType, service) flatMap { dataReturned =>
      validateAndCache(dataReturned = dataReturned, directMatch = false, Some(trimmedUtr), Some(orgType))
    }
  }

  private def getUserUtrAndType(implicit authContext: StandardAuthRetrievals): Option[(String, String)] = {
    (authContext.saUtr, authContext.ctUtr) match {
      case (Some(sa), None) => Some(sa -> "sa")
      case (None, Some(ct)) => Some(ct -> "org")
      case _ => None
    }
  }

  private def validateAndCache(dataReturned: JsValue, directMatch: Boolean, utr: Option[String],
                               orgType : Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
    val isFailureResponse = dataReturned.validate[MatchFailureResponse].isSuccess
    if (isFailureResponse) Future.successful(dataReturned)
    else {
      val isAnIndividual = (dataReturned \ "isAnIndividual").as[Boolean]
      if (isAnIndividual) cacheIndividual(dataReturned, directMatch, utr)
      else cacheOrg(dataReturned, directMatch, utr, orgType)
    }
  }

  private def cacheIndividual(dataReturned: JsValue,
                              directMatch: Boolean,
                              utr: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
    val businessType = SoleTrader
    val individual = (dataReturned \ "individual").as[Individual]
    val addressReturned = getAddress(dataReturned)

    val address = Address(line_1 = addressReturned.addressLine1, line_2 = addressReturned.addressLine2,
      line_3 = addressReturned.addressLine3, line_4 = addressReturned.addressLine4,
      postcode = addressReturned.postalCode, country = addressReturned.countryCode)

    val reviewDetails = ReviewDetails(businessName = s"${individual.firstName} ${individual.lastName}",
      businessType = Some(businessType),
      businessAddress = address,
      sapNumber = getSapNumber(dataReturned),
      safeId = getSafeId(dataReturned),
      agentReferenceNumber = getAgentRefNum(dataReturned),
      //default value from model due to AWRS
      //      isAGroup = false,
      directMatch = directMatch,
      firstName = Some(individual.firstName),
      lastName = Some(individual.lastName),
      utr = utr
    )

    dataCacheConnector.saveReviewDetails(reviewDetails) map { _ =>
      Json.toJson(reviewDetails)
    }
  }

  private[services] def cacheOrg(dataReturned: JsValue, directMatch: Boolean, utr: Option[String],
                       orgType : Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
    val organisation = (dataReturned \ "organisation").as[OrganisationResponse]
    val businessType = {
      if(organisation.organisationType.isDefined){
        organisation.organisationType
      }else{
        orgType
      }
    }
    val businessName = organisation.organisationName
    val isAGroup = organisation.isAGroup
    val addressReturned = getAddress(dataReturned)
    val address = Address(line_1 = addressReturned.addressLine1, line_2 = addressReturned.addressLine2,
      line_3 = addressReturned.addressLine3, line_4 = addressReturned.addressLine4,
      postcode = addressReturned.postalCode, country = addressReturned.countryCode)
    val reviewDetails = ReviewDetails(
      businessName = businessName,
      businessType = businessType,
      isAGroup = isAGroup.getOrElse(false),
      directMatch = directMatch,
      businessAddress = address,
      sapNumber = getSapNumber(dataReturned),
      safeId = getSafeId(dataReturned),
      agentReferenceNumber = getAgentRefNum(dataReturned),
      utr = utr
    )

    dataCacheConnector.saveReviewDetails(reviewDetails) map { _ =>
      Json.toJson(reviewDetails)
    }
  }

  private def getAddress(dataReturned: JsValue): EtmpAddress = {
    val addressReturned = (dataReturned \ "address").asOpt[EtmpAddress]
    addressReturned.getOrElse(throw new RuntimeException(s"[BusinessMatchingService][getAddress] - No Address returned from ETMP"))
  }

  private def getSafeId(dataReturned: JsValue): String = {
    val safeId = (dataReturned \ "safeId").asOpt[String]
    safeId.getOrElse(throw new RuntimeException(s"[BusinessMatchingService][getSafeId] - No Safe Id returned from ETMP"))
  }

  private def getSapNumber(dataReturned: JsValue): String = {
    (dataReturned \ "sapNumber").asOpt[String].getOrElse("")
  }

  private def getAgentRefNum(dataReturned: JsValue): Option[String] = {
    (dataReturned \ "agentReferenceNumber").asOpt[String]
  }

}
