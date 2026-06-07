package com.vpnlab.controlplane.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "vpn-control")
data class VpnControlProperties(
    val scriptsDir: String,
    val clientCount: Int = 5,
)
