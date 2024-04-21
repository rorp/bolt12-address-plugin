package io.github.rorp.bolt12addressplugin

import fr.acinq.eclair.tor.Socks5ProxyParams
import org.scalatest.funsuite.AsyncFunSuite

import java.net.{InetSocketAddress, Socket}
import scala.util.Try

class OfferFetcherSpec extends AsyncFunSuite {

  val bolt12Address = Bolt12Address("satoshi@twelve.cash")

  var torAddress = InetSocketAddress.createUnresolved("localhost", 9050)

  test("fetch offer via DNS") {
    val offerFetcher = OfferFetcher.create("dns", None).get
    assert(offerFetcher.isInstanceOf[Dns])
    offerFetcher.fetchOffer(bolt12Address).map(_ => succeed)
  }

  test("fetch offer via DNS over HTTPS") {
    val offerFetcher = OfferFetcher.create("doh", None).get
    assert(offerFetcher.isInstanceOf[DnsOverHttps])
    offerFetcher.fetchOffer(bolt12Address).map(_ => succeed)
  }

  test("fetch offer via DNS over HTTPS with Tor") {
    if (!remoteHostIsListening(torAddress)) {
      cancel(s"Tor daemon is not running at $torAddress")
    }

    val offerFetcher = OfferFetcher.create("doh", Some(Socks5ProxyParams(
      address = torAddress,
      credentials_opt = None,
      randomizeCredentials = true,
      useForIPv4 = true,
      useForIPv6 = true,
      useForTor = true,
      useForWatchdogs = true,
      useForDnsHostnames = true
    ))).get
    assert(offerFetcher.isInstanceOf[DnsOverHttps])
    offerFetcher.fetchOffer(bolt12Address).map(_ => succeed)
  }

  private def remoteHostIsListening(address: InetSocketAddress): Boolean =
    Try {
      val socket = new Socket(address.getHostString, address.getPort)
      socket.close()
    }.isSuccess

}
