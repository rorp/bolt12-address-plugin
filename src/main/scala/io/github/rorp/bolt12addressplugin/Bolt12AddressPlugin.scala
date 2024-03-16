package io.github.rorp.bolt12addressplugin

import akka.http.scaladsl.server.Route
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.{Kit, Plugin, PluginParams, RouteProvider, Setup}
import grizzled.slf4j.Logging

class Bolt12AddressPlugin extends Plugin with RouteProvider with Logging {

  private var paymentHandler: PaymentHandler = _

  override def params: PluginParams = new PluginParams {
    override def name: String = "Bolt12AddressPlugin"
  }

  override def onSetup(setup: Setup): Unit = ()

  override def onKit(kit: Kit): Unit = {
    paymentHandler = PaymentHandler(kit)
  }

  override def route(eclairDirectives: EclairDirectives): Route = ApiHandlers.registerRoutes(paymentHandler, eclairDirectives)
}
