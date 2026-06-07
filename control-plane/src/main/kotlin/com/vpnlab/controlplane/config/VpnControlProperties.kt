package com.vpnlab.controlplane.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "vpn-control")
data class VpnControlProperties(
    val scriptsDir: String,
    val clientCount: Int = 5,
    val blocklist: BlocklistProperties = BlocklistProperties(),
    val diskControl: DiskControlProperties = DiskControlProperties(),
)

data class BlocklistProperties(
    val hostsPath: String = "/Users/insuchoe/Desktop/vpn-lab/server-data/blocklist/hosts",
    val corefilePath: String = "/Users/insuchoe/Desktop/vpn-lab/server-data/coredns/Corefile",
)

data class DiskControlProperties(
    val policyPath: String = "/Users/insuchoe/Desktop/vpn-lab/disk-control/config/policy.json",
    val logPath: String = "/Users/insuchoe/Desktop/vpn-lab/disk-control/logs/disk-events.jsonl",
    val processPattern: String = "disk-control-poc",
)
