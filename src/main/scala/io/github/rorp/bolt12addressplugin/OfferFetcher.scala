package io.github.rorp.bolt12addressplugin

import fr.acinq.eclair.randomBytes
import fr.acinq.eclair.tor.Socks5ProxyParams
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import org.minidns.hla.{DnssecResolverApi, ResolverResult}
import org.minidns.record.TXT
import sttp.client3.okhttp.OkHttpFutureBackend
import sttp.client3.{SttpBackend, SttpBackendOptions, UriContext, basicRequest}
import sttp.model.{HeaderNames, Uri}

import java.net.InetSocketAddress
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Try}

trait OfferFetcher {
  def fetchOffer(bolt12Address: Bolt12Address): Future[Offer]
}

object OfferFetcher {
  def create(kind: String, socksProxy_opt: Option[Socks5ProxyParams])(implicit ec: ExecutionContext): Try[OfferFetcher] = Try {
    kind.toLowerCase() match {
      case "dns" => new Dns
      case "doh" => new DnsOverHttps(socksProxy_opt)
    }

  }
}

class Dns extends OfferFetcher {
  override def fetchOffer(bolt12Address: Bolt12Address): Future[Offer] = {
    Future.fromTry(
      for {
        domainName <- bolt12Address.toDomainName
        dnsResponse <- Try(DnssecResolverApi.INSTANCE.resolve(domainName, classOf[TXT]))
        offerStrings <- extractOfferStrings(dnsResponse)
        parsedOffers = offerStrings.map(Offer.decode)
        offer <- (parsedOffers.find(_.isSuccess) match {
          case Some(tryOffer) => tryOffer
          case None => parsedOffers.find(_.isFailure).get
        })
      } yield offer)
  }

  private def extractOfferStrings(resolverResult: ResolverResult[TXT]): Try[Seq[String]] = Try {
    val answers = resolverResult.getAnswers
    if (answers.isEmpty) throw new RuntimeException("DNS response was empty")
    val iter = answers.iterator()
    var res = List[String]()
    while (iter.hasNext) {
      val txt = iter.next().getText
      val offerString = if (txt.startsWith(Bolt12Address.Prefix)) {
        txt.substring(Bolt12Address.Prefix.length)
      } else if (txt.startsWith(Bolt12Address.LegacyPrefix)) {
        txt.substring(Bolt12Address.LegacyPrefix.length)
      } else {
        throw new RuntimeException(s"invalid DNS data: `$txt`")
      }
      res = offerString :: res
    }
    res
  }
}

class DnsOverHttps(socksProxy_opt: Option[Socks5ProxyParams])(implicit ec: ExecutionContext) extends OfferFetcher {


  val BaseUri: Uri = uri"https://1.1.1.1/dns-query"
  val ReadTimeout: FiniteDuration = 10.seconds

  private val sttp = createSttpBackend(socksProxy_opt)

  private def extractOfferStrings(body: String): Try[Seq[String]] = Try {
    import io.github.rorp.bolt12addressplugin.DnsOverHttps.DnsResponse
    val serialization = org.json4s.jackson.Serialization
    implicit val formats = org.json4s.DefaultFormats
    val json = serialization.read[DnsResponse](body)
    val res = json.Answer.map { data =>
      val txt = {
        val data = json.Answer.headOption.getOrElse(throw new RuntimeException(s"invalid DNS response: $json")).data
        val data1 = if (data.startsWith("\"")) data.tail else data
        if (data1.endsWith("\"")) data1.init else data1
      }
      if (txt.startsWith(Bolt12Address.Prefix)) {
        txt.substring(Bolt12Address.Prefix.length)
      } else if (txt.startsWith(Bolt12Address.LegacyPrefix)) {
        txt.substring(Bolt12Address.LegacyPrefix.length)
      } else {
        throw new RuntimeException(s"invalid DNS data: `$txt`")
      }
    }
    res
  }

  override def fetchOffer(bolt12Address: Bolt12Address): Future[Offer] = {
    val parametrizedUri = BaseUri.addParam("name", bolt12Address.toDomainName.get).addParam("type", "TXT")
    val request = basicRequest
      .header(HeaderNames.Accept, "application/dns-json", replaceExisting = true)
      .readTimeout(ReadTimeout).get(parametrizedUri)
    for {
      response <- sttp.send(request)
    } yield {
      if (!response.code.isSuccess) throw new RuntimeException(s"Error performing DNS query: status code ${response.code}")
      val body = response.body.getOrElse(throw new RuntimeException(s"Error performing DNS query: invalid body ${response.body}"))
      val offerStrings = extractOfferStrings(body).get
      val parsedOffers = offerStrings.map(Offer.decode)
      parsedOffers.find(_.isSuccess) match {
        case Some(tryOffer) => tryOffer.get
        case None => parsedOffers.find(_.isFailure).get.get
      }
    }
  }

  private def createSttpBackend(socksProxy_opt: Option[Socks5ProxyParams]): SttpBackend[Future, _] = {
    val options = SttpBackendOptions(connectionTimeout = 30.seconds, proxy = None)
    val sttpBackendOptions: SttpBackendOptions = socksProxy_opt match {
      case Some(proxy) =>
        val proxyOptions = options.connectionTimeout(120.seconds)
        val host = proxy.address.getHostString
        val port = proxy.address.getPort
        if (proxy.randomizeCredentials)
          proxyOptions.socksProxy(host, port, username = randomBytes(16).toHex, password = randomBytes(16).toHex)
        else
          proxyOptions.socksProxy(host, port)
      case _ => options
    }
    OkHttpFutureBackend(sttpBackendOptions)
  }

}

object DnsOverHttps {
  case class DnsRecord(name: String, `type`: Int, TTL: Int, data: String)

  case class DnsResponse(Answer: Seq[DnsRecord])
}

object Main {
  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    println(Await.result(new DnsOverHttps(Some(Socks5ProxyParams(
      address = InetSocketAddress.createUnresolved("localhost", 9050),
      credentials_opt = None,
      randomizeCredentials = true,
      useForIPv4 = true,
      useForIPv6 = true,
      useForTor = true,
      useForWatchdogs = true,
      useForDnsHostnames = true
    ))).fetchOffer(Bolt12Address("satoshi@twelve.cash")), Duration.Inf))
  }

}