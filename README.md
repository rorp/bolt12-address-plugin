# BOLT12 Address Plugin

This [Eclair](https://github.com/ACINQ/eclair) plugin allows to pay to human-readable [LN addresses using BOLT12 offers stored in DNS TXT records](https://github.com/bitcoin/bips/blob/master/bip-0353.mediawiki).

It should be treated as a POC, because the BOLT12 address specification is not yet finalized, and the BOLT12 interoperability 
between Linting implementations is not at production level.

The plugin uses Cloudflare's DNS over HTTPS service hosted at 1.1.1.1 

## How to build

First you need to build its dependencies

```bash
git clone https://github.com/ACINQ/eclair.git

cd eclair/

git checkout v0.10.0

mvn install -DskipTests=true
```

Then build the plugin
```bash
git clone https://github.com/rorp/bolt12-address-plugin.git

cd bolt12-address-plugin/

mvn install
```

The `mvn` command will put the plugin's JAR file into `target` directory. 

## Hot to run

Simply add the JAR file name to the Eclair node command line:

```bash
<PATH_TO_YOUR_ECLAIR_INSTALLATION>/eclair-node.sh target/bolt12-address-0.10.0.jar
```

## Tor support

If Socks5 support is enabled in the Eclair config, the plugin will use it to connect over Tor automatically, no additional configuration is required.

## API

`fetchoffer` `--bolt12Address=<bolt12-address>` will fetch the offer associated with the given BOLT12 address from DNS

`paybolt12address` `--bolt12Address=<bolt12-address>` `--amountMsat=<amount-msats>` will pay the offer associated with the BOLT12 address
