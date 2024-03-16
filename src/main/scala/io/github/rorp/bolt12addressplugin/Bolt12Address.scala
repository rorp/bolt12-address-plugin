package io.github.rorp.bolt12addressplugin

import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import org.minidns.hla.{DnssecResolverApi, ResolverResult}
import org.minidns.record.TXT

import scala.util.Try

case class Bolt12Address(address: String) {
  import io.github.rorp.bolt12addressplugin.Bolt12Address.Prefix

  def toDomainName: Try[String] = Try {
    address.split("@") match {
      case Array(user, host) => s"$user.user._bitcoin-payment.$host"
      case _ => throw new IllegalArgumentException("invalid BOLT12 address")
    }
  }

  def fetchOffer(): Try[Offer] = {
    for {
      domain <- toDomainName
      dnsResponse <- Try(DnssecResolverApi.INSTANCE.resolve(domain, classOf[TXT]))
      offerStr <- extractOfferString(dnsResponse)
      offer <- Offer.decode(offerStr)
    } yield offer
  }

  private def extractOfferString(resolverResult: ResolverResult[TXT]): Try[String] = Try {
    val answers = resolverResult.getAnswers
    if (answers.isEmpty) throw new RuntimeException("DNS response was empty")
    if (answers.size > 1) throw new RuntimeException("too many DNS records")
    val txt = answers.iterator().next().getText
    if (!txt.startsWith(Prefix)) throw new RuntimeException(s"invalid DNS data: `$txt`")
    txt.substring(Prefix.length)
  }
}

object Bolt12Address {
  val Prefix = "bitcoin:b12="
}