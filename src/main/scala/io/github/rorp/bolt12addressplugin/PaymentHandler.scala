package io.github.rorp.bolt12addressplugin

import akka.actor.typed
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.{ClassicActorSystemOps, ClassicSchedulerOps, TypedActorRefOps}
import akka.util.Timeout
import com.softwaremill.quicklens.ModifyPimp
import fr.acinq.bitcoin.scalacompat.Crypto.PublicKey
import fr.acinq.bitcoin.scalacompat.Satoshi
import fr.acinq.eclair.payment.PaymentEvent
import fr.acinq.eclair.payment.send.OfferPayment
import fr.acinq.eclair.router.Router.RouteParams
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.{CltvExpiryDelta, Kit, MilliSatoshi, ToMilliSatoshiConversion}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

case class PaymentHandler(appKit: Kit) {

  implicit val ec: ExecutionContext = appKit.system.dispatcher
  implicit val scheduler: Scheduler = appKit.system.scheduler.toTyped

  // We constrain external identifiers. This allows uuid, long and pubkey to be used.
  private val externalIdMaxLength = 66

  def payOffer(offer: Offer,
               amount: MilliSatoshi,
               quantity: Long,
               externalId_opt: Option[String],
               maxAttempts_opt: Option[Int],
               maxFeeFlat_opt: Option[Satoshi],
               maxFeePct_opt: Option[Double],
               pathFindingExperimentName_opt: Option[String],
               connectDirectly: Boolean)(implicit timeout: Timeout): Future[UUID] = {
    payOfferInternal(offer, amount, quantity, None, Nil, externalId_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, pathFindingExperimentName_opt, connectDirectly, blocking = false).mapTo[UUID]
  }

  def payOfferBlocking(offer: Offer,
                       amount: MilliSatoshi,
                       quantity: Long,
                       externalId_opt: Option[String],
                       maxAttempts_opt: Option[Int],
                       maxFeeFlat_opt: Option[Satoshi],
                       maxFeePct_opt: Option[Double],
                       pathFindingExperimentName_opt: Option[String],
                       connectDirectly: Boolean)(implicit timeout: Timeout): Future[PaymentEvent] = {
    payOfferInternal(offer, amount, quantity, None, Nil, externalId_opt, maxAttempts_opt, maxFeeFlat_opt, maxFeePct_opt, pathFindingExperimentName_opt, connectDirectly, blocking = true).mapTo[PaymentEvent]
  }

  private def payOfferInternal(offer: Offer,
                               amount: MilliSatoshi,
                               quantity: Long,
                               trampolineNodeId_opt: Option[PublicKey],
                               trampolineAttempts: Seq[(MilliSatoshi, CltvExpiryDelta)],
                               externalId_opt: Option[String],
                               maxAttempts_opt: Option[Int],
                               maxFeeFlat_opt: Option[Satoshi],
                               maxFeePct_opt: Option[Double],
                               pathFindingExperimentName_opt: Option[String],
                               connectDirectly: Boolean,
                               blocking: Boolean)(implicit timeout: Timeout): Future[Any] = {
    if (externalId_opt.exists(_.length > externalIdMaxLength)) {
      return Future.failed(new IllegalArgumentException(s"externalId is too long: cannot exceed $externalIdMaxLength characters"))
    }
    val routeParams = getRouteParams(pathFindingExperimentName_opt) match {
      case Right(defaultRouteParams) =>
        defaultRouteParams
          .modify(_.boundaries.maxFeeProportional).setToIfDefined(maxFeePct_opt.map(_ / 100))
          .modify(_.boundaries.maxFeeFlat).setToIfDefined(maxFeeFlat_opt.map(_.toMilliSatoshi))
      case Left(t) => return Future.failed(t)
    }
    val trampoline = trampolineNodeId_opt.map(trampolineNodeId => OfferPayment.TrampolineConfig(trampolineNodeId, trampolineAttempts))
    val sendPaymentConfig = OfferPayment.SendPaymentConfig(externalId_opt, connectDirectly, maxAttempts_opt.getOrElse(appKit.nodeParams.maxPaymentAttempts), routeParams, blocking, trampoline)
    val offerPayment = appKit.system.spawnAnonymous(OfferPayment(appKit.nodeParams, appKit.postman, appKit.router, appKit.paymentInitiator))
    offerPayment.ask((ref: typed.ActorRef[Any]) => OfferPayment.PayOffer(ref.toClassic, offer, amount, quantity, sendPaymentConfig)).flatMap {
      case f: OfferPayment.Failure => Future.failed(new Exception(f.toString))
      case x => Future.successful(x)
    }
  }

  private def getRouteParams(pathFindingExperimentName_opt: Option[String]): Either[IllegalArgumentException, RouteParams] = {
    pathFindingExperimentName_opt match {
      case None => Right(appKit.nodeParams.routerConf.pathFindingExperimentConf.getRandomConf().getDefaultRouteParams)
      case Some(name) => appKit.nodeParams.routerConf.pathFindingExperimentConf.getByName(name) match {
        case Some(conf) => Right(conf.getDefaultRouteParams)
        case None => Left(new IllegalArgumentException(s"Path-finding experiment ${pathFindingExperimentName_opt.get} does not exist."))
      }
    }
  }
}
