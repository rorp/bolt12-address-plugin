package io.github.rorp.bolt12addressplugin

import akka.http.scaladsl.server.Route
import fr.acinq.bitcoin.scalacompat.Satoshi
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.api.serde.FormParamExtractors._

object ApiHandlers {

  def registerRoutes(paymentHandler: PaymentHandler, eclairDirectives: EclairDirectives, offerFetcher: OfferFetcher): Route = {
    import eclairDirectives._
    import fr.acinq.eclair.api.serde.JsonSupport.{formats, marshaller, serialization}
    implicit val ec = paymentHandler.appKit.system.dispatcher


    val payBolt12Address: Route = postRequest("paybolt12address") { implicit t =>
      formFields("bolt12Address".as[String], amountMsatFormParam, "quantity".as[Long].?, "maxAttempts".as[Int].?, "maxFeeFlatSat".as[Satoshi].?, "maxFeePct".as[Double].?, "externalId".?, "pathFindingExperimentName".?, "connectDirectly".as[Boolean].?, "blocking".as[Boolean].?) {
        case (address, amountMsat, quantity_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, externalId_opt, pathFindingExperimentName_opt, connectDirectly, blocking_opt) =>
          complete(
            for {
              offer <- offerFetcher.fetchOffer(Bolt12Address(address))
              res <- blocking_opt match {
                case Some(true) => paymentHandler.payOfferBlocking(offer, amountMsat, quantity_opt.getOrElse(1), externalId_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, pathFindingExperimentName_opt, connectDirectly.getOrElse(false))
                case _ => paymentHandler.payOffer(offer, amountMsat, quantity_opt.getOrElse(1), externalId_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, pathFindingExperimentName_opt, connectDirectly.getOrElse(false))
              }
            } yield res
          )
      }
    }


    val fetchOffer: Route = postRequest("fetchoffer") { implicit t =>
      formFields("bolt12Address".as[String]) { (address: String) =>
        complete(offerFetcher.fetchOffer(Bolt12Address(address)))
      }
    }

    payBolt12Address ~ fetchOffer
  }

}

