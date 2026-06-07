package com.vpnlab.controlplane.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "vpn-control")
data class VpnControlProperties(
    val scriptsDir: String,
    val clientCount: Int = 5,
    val blocklist: BlocklistProperties = BlocklistProperties(),
)

data class BlocklistProperties(
    val hostsPath: String = "/Users/insuchoe/Desktop/vpn-lab/server-data/blocklist/hosts",
    val corefilePath: String = "/Users/insuchoe/Desktop/vpn-lab/server-data/coredns/Corefile",
)
