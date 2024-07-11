package io.github.rorp.bolt12addressplugin

import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import org.minidns.hla.{DnssecResolverApi, ResolverResult}
import org.minidns.record.TXT

import scala.util.Try

case class Bolt12Address(address: String) {
  def toDomainName: Try[String] = Try {
    address.split("@") match {
      case Array(user, host) => s"$user.user._bitcoin-payment.$host"
      case _ => throw new IllegalArgumentException("invalid BOLT12 address")
    }
  }
}

object Bolt12Address {
  val Prefix = "bitcoin:?lno="
  val LegacyPrefix = "bitcoin:b12="
}