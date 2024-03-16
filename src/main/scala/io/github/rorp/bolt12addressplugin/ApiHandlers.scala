package io.github.rorp.bolt12addressplugin

import akka.http.scaladsl.server.Route
import fr.acinq.bitcoin.scalacompat.Satoshi
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.api.serde.FormParamExtractors._

object ApiHandlers {

  def registerRoutes(paymentHandler: PaymentHandler, eclairDirectives: EclairDirectives): Route = {
    import eclairDirectives._
    import fr.acinq.eclair.api.serde.JsonSupport.{formats, marshaller, serialization}


    val payBolt12Address: Route = postRequest("paybolt12address") { implicit t =>
      formFields("bolt12Address".as[String], amountMsatFormParam, "quantity".as[Long].?, "maxAttempts".as[Int].?, "maxFeeFlatSat".as[Satoshi].?, "maxFeePct".as[Double].?, "externalId".?, "pathFindingExperimentName".?, "connectDirectly".as[Boolean].?, "blocking".as[Boolean].?) {
        case (address, amountMsat, quantity_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, externalId_opt, pathFindingExperimentName_opt, connectDirectly, blocking_opt) =>
          val offer = Bolt12Address(address).fetchOffer().get
          blocking_opt match {
            case Some(true) => complete(paymentHandler.payOfferBlocking(offer, amountMsat, quantity_opt.getOrElse(1), externalId_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, pathFindingExperimentName_opt, connectDirectly.getOrElse(false)))
            case _ => complete(paymentHandler.payOffer(offer, amountMsat, quantity_opt.getOrElse(1), externalId_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, pathFindingExperimentName_opt, connectDirectly.getOrElse(false)))
          }
      }
    }

    val fetchOffer: Route = postRequest("fetchoffer") { implicit t =>
      formFields("bolt12Address".as[String]) { (address: String) =>
        val offer = Bolt12Address(address).fetchOffer().get
        complete(offer)
      }
    }

    payBolt12Address ~ fetchOffer
  }

}

